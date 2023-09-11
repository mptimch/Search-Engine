package searchengine.services;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.jsoup.Connection;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.net.SocketTimeoutException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.RecursiveTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RequiredArgsConstructor
@Data
public class SiteParserForkJoin extends RecursiveTask<CopyOnWriteArrayList<Page>> {

    private final PageRepository pageRepository;
    private final SiteRepository siteRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;

    private final Site site;
    private final String path;

    private final CopyOnWriteArrayList<Page> pageList;

    // a boolean value indicating that we are stopping indexing
    public static boolean shutdownForkJoin;
    public boolean parseSinglePage;

    // HashSet to avoid repeated page parsing
    private final CopyOnWriteArrayList<String> doneUrls;
    private final CopyOnWriteArrayList<String> taskUrls;


    @Override
    protected CopyOnWriteArrayList<Page> compute() {
        if (shutdownForkJoin) {
            return new CopyOnWriteArrayList<>();
        }

        // creating connection and the necessary variables
        Connection.Response response = null;
        String pathToDatabase = AbsolutePathToRelative(path, site);
        Elements urls = new Elements();
        String pageContent = "";
        int statusCode = 0;

        try {
            Thread.sleep(2000);
            response = Jsoup.connect(path)
                    .userAgent("Mozilla/5.0 (Windows; U; WindowsNT 5.1; en-US; rv1.8.1.6) Gecko/20070725 Firefox/2.0.0.6")
                    .referrer("http://www.google.com")
                    .timeout(1500)
                    .execute();
            statusCode = response.statusCode();
        } catch (HttpStatusException e) {
            savePageWithConnectionError(path, site, e.getStatusCode(), e.getMessage());
            return new CopyOnWriteArrayList<>();
        } catch (SocketTimeoutException e) {
            savePageWithConnectionError(path, site, 504, e.getMessage());
            return new CopyOnWriteArrayList<>();
        } catch (Exception e) {
            savePageWithConnectionError(path, site, 00, e.getMessage());
            return new CopyOnWriteArrayList<>();
        }

        // getting page content
        try {
            Document doc = response.parse();
            urls = doc.select("a[href]");
            pageContent = doc.html();
        } catch (Exception e) {
            System.out.println("Ошибка при парсинге страницы " + path);
            doneUrls.add(path);
            doneUrls.add(pathToDatabase);
            savePageWithConnectionError(path, site, statusCode, e.getMessage());
            return new CopyOnWriteArrayList<>();
        }

        if (parseSinglePage) {
            Page page = createPage(path, site, statusCode, pageContent);
            pageRepository.save(page);
            ArrayList<Lemma> lemmas = createAndSaveLemmas(page, site);
            createAndSaveIndexes(lemmas, page);
            return new CopyOnWriteArrayList<>();
        }

        List<SiteParserForkJoin> subTaskList = new ArrayList<>();
        urls.forEach(element -> {
            String url = checkUrl(element.attr("href"), site.getUrl());
            if (url.length() < 2 || doneUrls.contains(url) || taskUrls.contains(url)) {
                return;
            }

            SiteParserForkJoin task = new SiteParserForkJoin(pageRepository, siteRepository, lemmaRepository, indexRepository, site, url, pageList, doneUrls, taskUrls);
            task.fork();
            subTaskList.add(task);
            taskUrls.add(url);
        });


        // checking this task in the completed or planned lists
        Page page = new Page();
        if (!doneUrls.contains(path) || !doneUrls.contains(pathToDatabase)) {
            page = createPage(path, site, statusCode, pageContent);
            pageList.add(page);
            doneUrls.add(path);
            doneUrls.add(pathToDatabase);
            taskUrls.remove(path);
        } else return new CopyOnWriteArrayList<>();

        boolean isUnique = savePageToDatabase(page);
        if (isUnique) {
            ArrayList<Lemma> lemmas = createAndSaveLemmas(page, page.getSite());
            createAndSaveIndexes(lemmas, page);
        }

        if (subTaskList.isEmpty()) {
            return new CopyOnWriteArrayList<>();
        }

        for (SiteParserForkJoin task : subTaskList) {
            task.join();
        }
        pageList.add(page);
        return pageList;
    }


    public boolean savePageToDatabase(Page page) {
        if (!pageRepository.existsByPathAndSite(page.getPath(), page.getSite())) {
            pageRepository.save(page);
            return true;
        }
        return false;
    }

    @Transactional
    public ArrayList<Lemma> createAndSaveLemmas(Page page, Site site) {
        synchronized (lemmaRepository) {
            ArrayList<Lemma> lemmas = createLemmas(page, site);
            lemmaRepository.saveAll(lemmas);
            return lemmas;
        }
    }

    @Transactional
    public void createAndSaveIndexes(ArrayList<Lemma> lemmas, Page page) {
        ArrayList<Index> indexes = createIndexObjects(lemmas, page);
        indexRepository.saveAll(indexes);
    }


    private void savePageWithConnectionError(String path, Site site, int statusCode, String errorMessage) {
        String pathToDatabase = AbsolutePathToRelative(path, site);
        doneUrls.add(path);
        doneUrls.add(pathToDatabase);
        taskUrls.remove(path);
        Page page = new Page();
        page.setSite(site);
        page.setPath(pathToDatabase);
        page.setCode(statusCode);
        if (statusCode == 200 || statusCode == 0) {
            page.setContent(errorMessage);
            site.setLastError(errorMessage);
        } else {
            page.setContent("Error " + statusCode + " on the page");
            String lastError = getStatusCodeMessage(statusCode);
            site.setLastError(lastError);
        }
        if (shutdownForkJoin) {
            page.setContent("Индексация остановлена пользователем");
            page.setCode(00);
            site.setLastError("Индексация остановлена пользователем");
        }
        site.setStatusTime(LocalDateTime.now());
        savePageToDatabase(page);
        siteRepository.save(site);
        doneUrls.add(path);
    }

    private Page createPage(String path, Site site, int statusCode, String content) {
        String newPath = AbsolutePathToRelative(path, site);
        Page page = new Page();
        page.setSite(site);
        page.setPath(newPath);
        page.setCode(statusCode);
        page.setContent(content);
        return page;
    }

    private ArrayList<Lemma> createLemmas(Page page, Site site) {
        ArrayList<Lemma> lemmas = new ArrayList<>();
        HashMap<String, Integer> lemmasMap = getLemmasMap(page);
        for (Map.Entry<String, Integer> entry : lemmasMap.entrySet()) {
            Optional<Lemma> optionalLemma = lemmaRepository.findLemmaByLemma(entry.getKey());
            Lemma lemma = new Lemma();

            if (optionalLemma.isPresent()) {
                lemma = optionalLemma.get();
                lemma.setFrequency(lemma.getFrequency() + entry.getValue());
                lemmas.add(lemma);
                continue;
            }

            lemma.setSite(site);
            lemma.setLemma(entry.getKey());
            lemma.setFrequency(entry.getValue());
            lemmas.add(lemma);
        }
        return lemmas;
    }

    private ArrayList<Index> createIndexObjects(ArrayList<Lemma> lemmas, Page page) {
        ArrayList<Index> indexes = new ArrayList<>();
        HashMap<String, Integer> lemmasMap = getLemmasMap(page);
        lemmas.forEach(lemma -> {
            Index index = new Index();
            index.setLemma(lemma);
            int rank = lemmasMap.get(lemma.getLemma());
            index.setRank((float) rank);
            index.setPage(page);
            indexes.add(index);
        });
        return indexes;
    }

    private HashMap<String, Integer> getLemmasMap(Page page) {
        LemmaService lemmaService = null;
        try {
            lemmaService = new LemmaService(new RussianLuceneMorphology());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return lemmaService.createLemma(page.getContent());
    }


    // метод изменения урла с абсолютного на относительный. В БД пишем относительный
    public static String AbsolutePathToRelative(String path, Site site) {
        if (path == site.getUrl()) {
            return "/";
        }
        String newPath = "";
        Pattern pattern = Pattern.compile(site.getUrl());
        Matcher matcher = pattern.matcher(path);
        int end = 0;
        while (matcher.find()) {
            end = matcher.end();
        }
        newPath = path.substring(end);

        if (newPath.isEmpty()) {
            return "/";
        }
        return newPath;
    }


    private static String checkUrl(String url, String domain) {
        String correctUrl = "";
        if (url.startsWith("/") && url.length() > 3) {
            correctUrl = domain + url;
        }

        if (url.startsWith(domain)) {
            correctUrl = url;
        }

        if (url.contains("#") || url.endsWith(".pdf") || url.endsWith(".jpg") || url.endsWith(".jpeg")
                || url.endsWith(".png") || url.endsWith(".bmp")) {
            return "";
        }

        if (url.contains("?utm") || url.contains("&utm")) {
            Pattern pattern = Pattern.compile("(?<=^.{0,})(?=[?]utm_)");
            Matcher matcher = pattern.matcher(url);
            String noUtm = "";
            if (matcher.find()) {
                noUtm = url.substring(0, matcher.start());
            }
            SiteParserForkJoin.checkUrl(noUtm, domain);
        }
        return correctUrl;
    }


    private static String getStatusCodeMessage(int statuscode) {
        switch (statuscode) {
            case 400:
                return "error 404: Bad Request";
            case 401:
                return "error 401: Unauthorized";
            case 403:
                return "error 403: Forbidden";
            case 404:
                return "error 404: Not found";
            case 500:
                return "error 500: Internal server error";
            case 502:
                return "error 502: Bad Gateway";
            case 503:
                return "error 503: Service unavailable";
            case 504:
                return "error 504: Gateway Timeout";
        }
        return "error " + statuscode + ": unknown error";
    }

}
