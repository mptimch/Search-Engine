package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.http.converter.json.MappingJacksonValue;
import org.springframework.stereotype.Service;
import searchengine.dto.statistics.PagesToResponse;
import searchengine.dto.statistics.PartialView;
import searchengine.dto.statistics.SearchResponse;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.text.BreakIterator;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

    private final PageRepository pageRepository;
    private final SiteRepository siteRepository;
    private final LemmaService lemmaService;
    private final IndexRepository indexRepository;
    private final LemmaRepository lemmaRepository;


    // this value is the frequency of the lemma on all pages to the total number of lemmas. We ignore popular lemmas
    double popularLemma = 60.0;
    int snippetLengthLimit = 180;


    @Override
    public SearchResponse search(String query, String site, Integer offset, Integer limit) {
        SearchResponse response = new SearchResponse();
        long pages = pageRepository.count();

        // breaking the query into words, convert them into objects Lemma
        Set<String> querySet = lemmaService.createLemma(query).keySet();
        HashMap<Lemma, Double> lemmasWithPopularity = createLemmasWithPopularity(querySet, pages, site);

        // sort the map by lemma's popularity
        LinkedHashMap<Lemma, Double> sortedLemmaWithPopularity = lemmasWithPopularity.entrySet()
                .stream()
                .sorted(Map.Entry.comparingByValue())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new
                ));

        ArrayList<Page> relevantPages = findRelevantPages(sortedLemmaWithPopularity);
        if (relevantPages.isEmpty()) {
            response.setResult(true);
            response.setCount(0);
            response.setData(new ArrayList<PagesToResponse>());
            return response;
        }

        // calculating relevance for the each page
        ArrayList<Lemma> lemmas = new ArrayList<>(sortedLemmaWithPopularity.keySet());
        LinkedHashMap<Page, Double> pagesWithRelewanth = getRelevanthToPages(relevantPages, lemmas);
        LinkedHashMap<Page, Double> pagesWithSortRelevance = pagesWithRelewanth.entrySet()
                .stream()
                .sorted(Map.Entry.<Page, Double>comparingByValue().reversed())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new
                ));

        fillTheResponse(pagesWithSortRelevance, response, lemmas, limit, offset);
        return response;
    }


    // breaking the query into words ignoring the most popular lemmas.
    // returns a HashMap with Lemma as the key and popularity percent as the value.
    private HashMap<Lemma, Double> createLemmasWithPopularity(Set<String> querySet, long pages, String siteStringFromQuery) {
        HashMap<Lemma, Double> lemmasWithPopularity = new HashMap<>();
        querySet.forEach(word -> {
            Lemma lemma = new Lemma();

            // if searching within the site
            if (siteStringFromQuery != null && !siteStringFromQuery.isBlank()) {
                Optional<Site> site = siteRepository.findByUrl(siteStringFromQuery);
                Optional<Lemma> optionalLemma = site.flatMap(siteFromRepo -> lemmaRepository.findLemmaByLemmaAndSite(word, siteFromRepo));
                if (optionalLemma.isPresent()) {
                    lemma = optionalLemma.get();
                } else return;
            }

            // if searching within all site
            else {
                Optional<Lemma> optional = lemmaRepository.findLemmaByLemma(word);
                if (optional.isPresent()) {
                    lemma = optional.get();
                } else return;
            }

            // removing popular lemma
            long lemmaOnPages = indexRepository.countPagesByLemma(lemma);
            double lemmaPopularity = (double) pages / (double) lemmaOnPages;
            if (lemmaPopularity < popularLemma) {
                lemmasWithPopularity.put(lemma, lemmaPopularity);
            }
        });
        return lemmasWithPopularity;
    }


    private ArrayList<Page> findRelevantPages(LinkedHashMap<Lemma, Double> sortedMap) {
        ArrayList<Page> pages = new ArrayList<>();
        List<Lemma> keysList = new ArrayList<>(sortedMap.keySet());

        for (Lemma lemma : keysList) {
            // get pagesList for the first or not first lemma
            if (pages.isEmpty()) {
                List<Page> pageList = indexRepository.findDistinctPagesByLemma(lemma);
                pages = pageList.isEmpty() ? new ArrayList<>() : (ArrayList<Page>) pageList;
            } else {
                Optional<List<Page>> newPageList = indexRepository.findDistinctPageByLemmaAndPageIn(lemma, pages);
                if (newPageList.isPresent() && !newPageList.get().isEmpty()) pages.addAll(newPageList.get());
            }
            if (pages.isEmpty()) return pages;
        }
        return pages;
    }
    
    private LinkedHashMap<Page, Double> getRelevanthToPages(ArrayList<Page> relevantPages, ArrayList<Lemma> lemmasList) {
        LinkedHashMap<Page, Integer> pagesWithAbsoluteRelevanth = new LinkedHashMap<>();
        long maxRelevance = 0;
        
        for (Page page : relevantPages) {
            ArrayList<String> lemmasOnThePage = lemmaService.getLemmasListFromPage(page);
            int absoluteRelevance = getLemmasCount(lemmasOnThePage, lemmasList);
            maxRelevance = Math.max(absoluteRelevance, maxRelevance);
            pagesWithAbsoluteRelevanth.put(page, absoluteRelevance);
        }
        
        LinkedHashMap<Page, Double> pagesWithRelevanth = new LinkedHashMap<>();
        for (Map.Entry<Page, Integer> entry : pagesWithAbsoluteRelevanth.entrySet()) {
            double relativeRelevance = (double) entry.getValue() / (double) maxRelevance;
            pagesWithRelevanth.put(entry.getKey(), relativeRelevance);
        }
        return pagesWithRelevanth;
    }


    private int getLemmasCount(ArrayList<String> lemmasOnThePage, ArrayList<Lemma> lemmasList) {
        int relevance = 0;
        for (Lemma lemma : lemmasList) {
            String lemmaWord = lemma.getLemma();
            for (String wordOnPage : lemmasOnThePage) {
                if (wordOnPage.equals(lemmaWord)) {
                    relevance++;
                }
            }
        }
        return relevance;
    }

    private SearchResponse fillTheResponse(LinkedHashMap<Page, Double> pagesWithRelewanth,
                                           SearchResponse response,
                                           ArrayList<Lemma> lemmas,
                                           Integer limit,
                                           Integer offset
    ) {
        limit = limit != null ? limit : 20;
        response.setResult(true);
        response.setCount(pagesWithRelewanth.size());
        List<PagesToResponse> data = new ArrayList<>();

        for (Map.Entry<Page, Double> entry : pagesWithRelewanth.entrySet()) {
            PagesToResponse pageToResponse = new PagesToResponse();
            pageToResponse.setSite(entry.getKey().getSite().getUrl());
            pageToResponse.setSitename(entry.getKey().getSite().getName());
            pageToResponse.setUri(entry.getKey().getPath());
            pageToResponse.setRelevance(entry.getValue());
            pageToResponse.setTitle(createTitle(entry.getKey()));
            pageToResponse.setSnippet(createSnippet(entry.getKey(), lemmas));

            if (data.size() <= limit) {
                data.add(pageToResponse);
            }
        }
        if (offset != null && offset < data.size()) {
            data = data.subList(offset, data.size());
        }
        response.setData(data);
        return response;
    }


    private String createTitle(Page page) {
        String content = page.getContent();
        Document doc = Jsoup.parse(content);
        String title = doc.title();
        String cleanedTitle = Jsoup.parse(title).text();
        return cleanedTitle;
    }


    private String createSnippet(Page page, ArrayList<Lemma> lemmas) {
        List<String> lemmasList = lemmaRepository.findLemmasByLemmaIn(lemmas);
        Document doc = Jsoup.parse(page.getContent());
        String title = doc.title();
        String body = doc.body().text();
        String text = title + ".\n" + body;
        String snippet = "";

        ArrayList<String> sentences = splitTextIntoSentences(text);
        int maxcount = 0;
        String bestSnippet = "";

        // for each sentence comparing the number of lemmas matches
        for (String sentence : sentences) {
            int currentMatchCount = 0;
            ArrayList<String> wordList = new ArrayList<>(Arrays.asList(sentence.toLowerCase().split("\\s+")));

            // highlight the desired word in bold
            for (int i = 0; i < wordList.size(); i++) {
                String word = wordList.get(i);
                String baseWord = lemmaService.getBaseWord(word);
                if (lemmasList.contains(baseWord)) {
                    String newWord = "<b>" + wordList.get(i) + "</b>";
                    wordList.set(i, newWord);
                    currentMatchCount++;
                }
            }

            if (currentMatchCount > maxcount) {
                bestSnippet = String.join(" ", wordList);
                maxcount = currentMatchCount;
                snippet = bestSnippet.length() < snippetLengthLimit ? bestSnippet : cutSnippet(bestSnippet);
            }
        }
        return snippet;
    }


    private ArrayList<String> splitTextIntoSentences(String text) {
        ArrayList<String> sentences = new ArrayList<>();
        BreakIterator breakIterator = BreakIterator.getSentenceInstance();
        breakIterator.setText(text);

        int start = breakIterator.first();
        int end = breakIterator.next();
        while (end != BreakIterator.DONE) {
            sentences.add(text.substring(start, end).trim());
            start = end;
            end = breakIterator.next();
        }
        return sentences;
    }

    private String cutSnippet(String text) {
        int highlightIndex = text.indexOf("<b>");

        if (highlightIndex <= snippetLengthLimit) {
            return text.substring(0, snippetLengthLimit) + "...";
        } else if ((highlightIndex + snippetLengthLimit) > text.length()) {
            return "... " + text.substring(highlightIndex);
        } else {
            return "... " + text.substring(highlightIndex, highlightIndex + snippetLengthLimit - 3) + " ...";
        }
    }

    public MappingJacksonValue createResponseForNullQuery(SearchResponse response) {
        response.setError("Задан пустой поисковый запрос");
        response.setResult(false);
        MappingJacksonValue jacksonValue = new MappingJacksonValue(response);
        jacksonValue.setSerializationView(PartialView.class);
        return jacksonValue;
    }
}


