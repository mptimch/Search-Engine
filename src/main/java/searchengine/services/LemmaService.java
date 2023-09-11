package searchengine.services;

import org.apache.lucene.morphology.LuceneMorphology;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import searchengine.model.Page;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class LemmaService {

    private final LuceneMorphology morphology;
    private static final ArrayList<String> particlesNames = new ArrayList<>(
            Arrays.asList("МЕЖД", "ПРЕДЛ", "СОЮЗ", "ЧАСТ"));


    public LemmaService(LuceneMorphology luceneMorphology) {
        this.morphology = luceneMorphology;
    }

    // main method that returns Map with unique lemmas and their repetition rate
    public HashMap<String, Integer> createLemma(String text) {
        String noHtmlText = cleanFromHtml(text);
        ArrayList<String> separatedText = separateText(noHtmlText);
        ArrayList<String> baseWordsList = getBaseWordsList(separatedText);
        HashMap<String, Integer> lemmas = collectLemmas(baseWordsList);
        return lemmas;
    }


    public String cleanFromHtml(String text) {
        Document document = Jsoup.parse(text);
        String noHtmlText = document.text();
        return noHtmlText;
    }


    public ArrayList<String> separateText(String text) {
        ArrayList<String> wordsList = new ArrayList<>();

        // looking only for Cyrillic words
        Pattern pattern = Pattern.compile("\\b[а-яА-Я]+\\b");
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            String word = matcher.group().toLowerCase();
            wordsList.add(word);
        }
        return wordsList;
    }


    public ArrayList<String> getBaseWordsList(ArrayList<String> text) {
        ArrayList<String> baseForms = new ArrayList<>();
        for (String word : text) {

            if (word.isBlank()) {
                continue;
            }

            List<String> wordInfoList = morphology.getMorphInfo(word);
            String[] wordInfo = wordInfoList.toString().replaceAll("]", "").split("\\s");
            if (particlesNames.contains(wordInfo[1])) {
                continue;
            }
            String normalForm = morphology.getNormalForms(word).get(0);
            baseForms.add(normalForm);
        }
        return baseForms;
    }

    public String getBaseWord(String word) {
        String result = "";
        try {
            result = morphology.getNormalForms(word).get(0);
        } catch (Exception e) {
            return result;
        }
        result = result.replaceAll("\\d", "");
        if (result.length() < 3) {
            return "";
        }
        return result;
    }


    private HashMap<String, Integer> collectLemmas(ArrayList<String> baseWordsList) {
        HashMap<String, Integer> lemmas = new HashMap<>();
        for (String word : baseWordsList) {
            if (!lemmas.containsKey(word)) {
                lemmas.put(word, 1);
            } else lemmas.put(word, lemmas.get(word) + 1);
        }
        return lemmas;
    }

    // creating a list of lemmas from a page. Lemmas repetitions are possible
    public ArrayList<String> getLemmasListFromPage(Page page) {
        String noHtmlText = cleanFromHtml(page.getContent());
        ArrayList<String> separatedText = separateText(noHtmlText);
        ArrayList<String> baseWordsList = getBaseWordsList(separatedText);
        return baseWordsList;
    }
}
