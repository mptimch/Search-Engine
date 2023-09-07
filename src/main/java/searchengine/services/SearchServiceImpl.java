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


    // это значение - частота появления леммы на страницах от общего количества.
    // другими словами: если лемма встречается на 60% страниц или более - мы ее не учитываем в поисковом запросе
    double popularLemma = 60.0;

    // Максимальный размер сниппета
    int snippetLengthLimit = 180;


    @Override
    public SearchResponse search(String query, String site, Integer offset, Integer limit) {
        SearchResponse response = new SearchResponse();

        long pages = pageRepository.count();

        // разбиваем запрос на слова, преобразуем в объекты Lemma
        Set<String> querySet = lemmaService.createLemma(query).keySet();
        HashMap<Lemma, Double> lemmasWithPopularity = createLemmas(querySet, pages, site);

        // сортируем леммы в Map по возрастанию популярности
        LinkedHashMap<Lemma, Double> sortedLemmaWithPopularity = lemmasWithPopularity.entrySet()
                .stream()
                .sorted(Map.Entry.comparingByValue())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new
                ));

        // создаем список страниц, отвечающих запросу
        ArrayList<Page> relevantPages = findRelevantPages(sortedLemmaWithPopularity);

        // Если нет результатов по такому запросу - возвращаем пустой список
        if (relevantPages.isEmpty()) {
            response.setResult(true);
            response.setCount(0);
            response.setData(new ArrayList<PagesToResponse>());
            return response;
        }

        // просчитываем релевантность для каждой страницы
        ArrayList<Lemma> lemmas = new ArrayList<>(sortedLemmaWithPopularity.keySet());
        LinkedHashMap<Page, Double> pagesWithRelewanth = getRelevanthToPages(relevantPages, lemmas);

        // Сортируем значения в Map по релевантности
        LinkedHashMap<Page, Double> pagesWithSortRelevance = pagesWithRelewanth.entrySet()
                .stream()
                .sorted(Map.Entry.<Page, Double>comparingByValue().reversed()) // Сортировка по значению (по убыванию)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1, // Если значения одинаковые, оставляем первое
                        LinkedHashMap::new
                ));

        fillTheResponse(pagesWithSortRelevance, response, lemmas, limit, offset);


        return response;
    }


    // метод разбивает запрос на слова и отсеивает самые популярные леммы.
    // Возвращает HashMap с Lemma в качестве ключа и популярностью (на каком % страниц встречается) в значении.
    private HashMap<Lemma, Double> createLemmas(Set<String> querySet, long pages, String siteStringFromQuery) {
        HashMap<Lemma, Double> lemmasWithPopularity = new HashMap<>();
        querySet.forEach(word -> {
            Lemma lemma = new Lemma();

            // блок для ситуаций, когда ищем в пределах сайта
            if (siteStringFromQuery != null && !siteStringFromQuery.isBlank()) {
                Optional<Site> site = siteRepository.findByUrl(siteStringFromQuery);
                Optional<Lemma> optionalLemma = site.flatMap(siteFromRepo -> lemmaRepository.findLemmaByLemmaAndSite(word, siteFromRepo));
                if (optionalLemma.isPresent()) {
                    lemma = optionalLemma.get();
                } else return;
            }
            // блок для ситуаций, когда ищем по всем сайтам
            else {
                Optional<Lemma> optional = lemmaRepository.findLemmaByLemma(word);
                if (optional.isPresent()) {
                    lemma = optional.get();
                } else return;
            }

            // смотрим, как часто встречается лемма. Убираем наиболее часто встречаемые
            long lemmaOnPages = indexRepository.countPagesByLemma(lemma);
            double lemmaPopularity = (double) pages / (double) lemmaOnPages;
            if (lemmaPopularity < popularLemma) {
                lemmasWithPopularity.put(lemma, lemmaPopularity);
            }
        });
        return lemmasWithPopularity;
    }


    // Метод собирает список страниц, релевантных леммам из запроса. Начинает с самой редкой леммы.
    private ArrayList<Page> findRelevantPages(LinkedHashMap<Lemma, Double> sortedMap) {
        ArrayList<Page> pages = new ArrayList<>();
        List<Lemma> keysList = new ArrayList<>(sortedMap.keySet());

        for (Lemma lemma : keysList) {

            // получаем сначала список страниц для 1й леммы
            if (pages.isEmpty()) {
                List<Page> pageList = indexRepository.findDistinctPagesByLemma(lemma);
                pages = pageList.isEmpty() ? new ArrayList<>() : (ArrayList<Page>) pageList;

                // а если в списке уже есть страницы - ищем лемму по ним
            } else {
                Optional<List<Page>> newPageList = indexRepository.findDistinctPageByLemmaAndPageIn(lemma, pages);
                if (newPageList.isPresent() && !newPageList.get().isEmpty()) pages.addAll(newPageList.get());
            }

            // если список пуст - нет смысла обращаться к репозиториям дальше
            if (pages.isEmpty()) return pages;
        }
        return pages;
    }


    // метод просчитывает релевантности для страниц
    private LinkedHashMap<Page, Double> getRelevanthToPages(ArrayList<Page> relevantPages, ArrayList<Lemma> lemmasList) {

        // считаем абсолютную релевантность для каждой страницы и максимальную сумму
        LinkedHashMap<Page, Integer> pagesWithAbsoluteRelevanth = new LinkedHashMap<>();
        long maxRelevance = 0;

        // считаем количество лемм для каждой страницы. Фиксируем максимум
        for (Page page : relevantPages) {
            ArrayList<String> lemmasOnThePage = lemmaService.createLemmasFromPage(page);
            int relevance = getLemmasCount(lemmasOnThePage, lemmasList);

            maxRelevance = Math.max(relevance, maxRelevance);
            pagesWithAbsoluteRelevanth.put(page, relevance);
        }

        // считаем относительную релевантность для каждой страницы
        LinkedHashMap<Page, Double> pagesWithRelevanth = new LinkedHashMap<>();
        for (Map.Entry<Page, Integer> entry : pagesWithAbsoluteRelevanth.entrySet()) {
            double relevance = (double) entry.getValue() / (double) maxRelevance;
            pagesWithRelevanth.put(entry.getKey(), relevance);
        }
        return pagesWithRelevanth;
    }


    // метод считает количество лемм на странице
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

    // метод наполняет и подготавливает для отдачи response
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

            //вывод ограниченного количества результатов
            if (data.size() <= limit) {
                data.add(pageToResponse);
            }
        }

        // вывод результатов с отступом
        if (offset != null && offset < data.size()) {
            data = data.subList(offset, data.size());
        }

        response.setData(data);
        return response;
    }


    // метод формирует title для Page. Необходимо для response
    private String createTitle(Page page) {
        String content = page.getContent();
        Document doc = Jsoup.parse(content);
        String title = doc.title();
        String cleanedTitle = Jsoup.parse(title).text();
        return cleanedTitle;
    }


    // метод формирует сниппет. Для этого разбиваем осмысленный текст (title и body) на предложения
    // и смотрим, какое лучше подходит.
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

        // по каждому предложению смотрим количество совпадений лемм
        for (String sentence : sentences) {
            int currentMatchCount = 0;
            ArrayList<String> wordList = new ArrayList<>(Arrays.asList(sentence.toLowerCase().split("\\s+")));

            // Сразу выделяем нужное слово жирным
            for (int i = 0; i < wordList.size(); i++) {
                String word = wordList.get(i);
                String baseWord = lemmaService.getBaseWord(word);
                if (lemmasList.contains(baseWord)) {
                    String newWord = "<b>" + wordList.get(i) + "</b>";
                    wordList.set(i, newWord);
                    currentMatchCount++;

                }
            }

            // если у этого предложения больше всего совпадений - возвращаем его
            if (currentMatchCount > maxcount) {
                bestSnippet = String.join(" ", wordList);
                maxcount = currentMatchCount;
                snippet = bestSnippet.length() < snippetLengthLimit ? bestSnippet : cutSnippet(bestSnippet);
            }
        }

        return snippet;
    }


    // метод разбивает текст на предложения
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

    // метод делает обрезку сниппета, чтобы хотя бы 1 выделенное слово в него попало
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

    // метод для тех случаев, когда передан пустой запрос
    public MappingJacksonValue createResponseForNullQuery(SearchResponse response) {
        response.setError("Задан пустой поисковый запрос");
        response.setResult(false);
        MappingJacksonValue jacksonValue = new MappingJacksonValue(response);
        jacksonValue.setSerializationView(PartialView.class);
        return jacksonValue;
    }
}


