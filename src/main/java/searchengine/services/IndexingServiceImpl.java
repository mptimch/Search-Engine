package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.config.SitesList;
import searchengine.dto.statistics.IndexingResponse;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.model.SiteIndexationStatus;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.net.URL;
import java.time.LocalDateTime;
import java.util.*;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class IndexingServiceImpl implements IndexingService {

    private final SitesList sites;
    private final PageRepository pageRepository;
    private final SiteRepository siteRepository;
    private final LemmaService lemmaService;
    private final IndexRepository indexRepository;
    private final LemmaRepository lemmaRepository;

    // HashMap with ForkJoinPool objects are keys (for checking status or stopping)
    // and SiteParserForkJoin objects - for getting unfinished tasks
    private HashMap<ForkJoinPool, SiteParserForkJoin> forkjoinMap = new HashMap<>();


    @Override
    public void startIndexing() {
        cleanDBBeforeNewIndexing();
        SiteParserForkJoin.shutdownForkJoin = false;

        sites.getSites().forEach(site -> {

            // creatind a new Site in database
            Site newSite = new Site();
            newSite.setStatus(SiteIndexationStatus.INDEXING);
            newSite.setName(site.getName());
            newSite.setUrl(site.getUrl());
            newSite.setStatusTime(LocalDateTime.now());
            siteRepository.save(newSite);

            // Creating lists of ForkJoinPool objects and our class that extends it
            ForkJoinPool forkJoinPool = new ForkJoinPool();
            SiteParserForkJoin siteParserForkJoin = new SiteParserForkJoin(
                    pageRepository,
                    siteRepository,
                    lemmaRepository,
                    indexRepository,
                    newSite,
                    newSite.getUrl(),
                    new CopyOnWriteArrayList<Page>(),
                    new CopyOnWriteArrayList<String>(),
                    new CopyOnWriteArrayList<String>());

            forkjoinMap.put(forkJoinPool, siteParserForkJoin);


            // starting ForkJoinPool
            CopyOnWriteArrayList<Page> pageList = forkJoinPool.invoke(siteParserForkJoin);
            if (!SiteParserForkJoin.shutdownForkJoin) {
                newSite.setStatus(SiteIndexationStatus.INDEXED);
                siteRepository.save(newSite);
            }
        });
    }

    @Override
    public void stopIndexing() {
        for (Map.Entry<ForkJoinPool, SiteParserForkJoin> map : forkjoinMap.entrySet()) {
            ForkJoinPool forkJoinPool = map.getKey();
            SiteParserForkJoin siteParserForkJoin = map.getValue();

            if (!forkJoinPool.isQuiescent()) {
                Site site = siteParserForkJoin.getSite();
                site.setLastError("Индексация остановлена пользователем");
                site.setStatusTime(LocalDateTime.now());
                site.setStatus(SiteIndexationStatus.FAILED);
                siteRepository.save(site);

                // getting uncomplete tasks, stopping ForkJoinPools
                ArrayList<Page> pages = getNotHandledPagesFromForkJoinPool(siteParserForkJoin);
                pages.forEach(siteParserForkJoin::savePageToDatabase);
                SiteParserForkJoin.shutdownForkJoin = true;
                forkJoinPool.shutdown();
            }
        }
    }


    @Override
    public IndexingResponse indexingPage(String url) {
        IndexingResponse response = new IndexingResponse();

        // validating URL
        if (!isValidUrl(url)) {
            response.setError("Введенный вами текст не является Url");
            return response;
        }
        if (!isSiteFromUrlsList(url)) {
            response.setError("Данная страница находится за пределами сайтов, указанных в конфигурационном файле");
            return response;
        }

        Site site = getOrCreateSite(url);
        String path = SiteParserForkJoin.AbsolutePathToRelative(url, site);
        Page page = new Page();

        // delete data from the database if the page has been indexed
        if (pageRepository.existsByPathAndSite(path, site)) {
            page = pageRepository.findByPathAndSite(path, site);
            removeAllPageInfoFromDatabase(page);
        }

        SiteParserForkJoin siteParserForkJoin = new SiteParserForkJoin(
                pageRepository,
                siteRepository,
                lemmaRepository,
                indexRepository,
                site,
                url,
                new CopyOnWriteArrayList<Page>(),
                new CopyOnWriteArrayList<String>(),
                new CopyOnWriteArrayList<String>(Arrays.asList(url))
        );
        // start parsing in a separate single thread
        siteParserForkJoin.parseSinglePage = true;
        SiteParserForkJoin.shutdownForkJoin = false;
        new Thread(() -> siteParserForkJoin.compute()).start();
        return response;
    }


    private void removeAllPageInfoFromDatabase(Page page) {
        String content = page.getContent();
        HashMap<String, Integer> lemmas = lemmaService.createLemma(content);
        decreaseLemmasInDatabase(lemmas);
        pageRepository.delete(page);
    }

    private void decreaseLemmasInDatabase(HashMap<String, Integer> lemmas) {
        ArrayList<Lemma> changedLemmas = new ArrayList<>();

        for (Map.Entry<String, Integer> entry : lemmas.entrySet()) {
            Lemma lemma = lemmaRepository.findLemmaByLemma(entry.getKey()).get();
            if (lemma != null) {
                lemma.setFrequency(lemma.getFrequency() - entry.getValue());
                changedLemmas.add(lemma);
            }
        }
        makeTransactionToDecreaseLemmas(changedLemmas);
    }

    @Transactional
    void makeTransactionToDecreaseLemmas(ArrayList<Lemma> changedLemmas) {
        changedLemmas.forEach(lemma -> lemmaRepository.save(lemma));
    }


    private boolean isValidUrl(String url) {
        try {
            new URL(url);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isSiteFromUrlsList(String url) {
        for (searchengine.config.Site site : sites.getSites()) {
            String domain = site.getUrl();
            if (url.startsWith(domain)) {
                return true;
            }
        }
        return false;
    }

    private Site getOrCreateSite(String url) {
        Site site = new Site();
        ArrayList<String> siteNameAndDomain = getSiteNameAndDomainByUrl(url);
        Optional<Site> optionalSite = siteRepository.findByUrl(siteNameAndDomain.get(1));

        if (!optionalSite.isPresent()) {
            site.setName(siteNameAndDomain.get(0));
            site.setUrl(siteNameAndDomain.get(1));
        } else {
            site = optionalSite.get();
        }
        site.setStatus(SiteIndexationStatus.INDEXING);
        site.setStatusTime(LocalDateTime.now());
        site.setLastError(null);
        siteRepository.save(site);
        return site;
    }

    private ArrayList<String> getSiteNameAndDomainByUrl(String url) {
        ArrayList<String> siteNameAndDomain = new ArrayList<>();
        for (searchengine.config.Site site : sites.getSites()) {
            if (url.startsWith(site.getUrl())) {
                siteNameAndDomain.add(site.getName());
                siteNameAndDomain.add(site.getUrl());
            }
        }
        return siteNameAndDomain;
    }

    // we need this method when reindexing
    private void cleanDBBeforeNewIndexing() {
        indexRepository.deleteAll();
        lemmaRepository.deleteAll();
        pageRepository.deleteAll();
        siteRepository.deleteAll();
        Site site = new Site();
        site.setStatus(SiteIndexationStatus.INDEXING);
    }

    // we need this method when stops indexing
    private ArrayList<Page> getNotHandledPagesFromForkJoinPool(SiteParserForkJoin siteParserForkJoin) {
        ArrayList<String> taskUrls = new ArrayList<>(siteParserForkJoin.getTaskUrls());

        if (taskUrls.isEmpty()) {
            return new ArrayList<>();
        }

        // removing duplicates
        taskUrls.stream().distinct().collect(Collectors.toList());

        ArrayList<Page> pages = new ArrayList<>();
        taskUrls.forEach(task -> {
            Page page = new Page();
            Site site = siteParserForkJoin.getSite();
            page.setSite(site);
            page.setPath(siteParserForkJoin.AbsolutePathToRelative(task, site));
            page.setCode(00);
            page.setContent("Индексация остановлена пользователем");
            pages.add(page);
        });
        return pages;
    }
}


