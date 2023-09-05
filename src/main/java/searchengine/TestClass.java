package searchengine;

import org.apache.lucene.morphology.Heuristic;
import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.net.URL;
import java.util.List;

public class TestClass {

    public static void main(String[] args) throws IOException {

        String oneUrl = "https://proglang.su/java/url-processing";
        String twoUrl = "not_a_url";

        if (isValidUrl(oneUrl)) {
            // Обработка валидного URL
            System.out.println("Valid URL received: " + oneUrl);
        } else {
            // Обработка невалидного URL
            System.out.println("Invalid URL received: " + oneUrl);

        }

        if (isValidUrl(twoUrl)) {
            // Обработка валидного URL
            System.out.println("Valid URL received: " + twoUrl);
        } else {
            // Обработка невалидного URL
            System.out.println("Invalid URL received: " + twoUrl);

        }
    }
            public static boolean isValidUrl(String urlString){
            try {
                new URL(urlString);
                return true;
            } catch (Exception e) {
                return false;
            }
        }




//        String chasticaOne = "кошки";
//        String chasticaTwo = "играя";
//        String chasticaThree = "краснея";
//
//        LuceneMorphology morphology= new RussianLuceneMorphology();
//        List<String> oneList = morphology.getMorphInfo(chasticaOne);
//        List<String> twoList = morphology.getMorphInfo(chasticaTwo);
//        List<String> threeList = morphology.getMorphInfo(chasticaThree);
//
//        String [] oneword = oneList.toString().replaceAll("]","").split("\\s");
//        String [] twoword = twoList.toString().replaceAll("]","").split("\\s");
//        String [] threeword = threeList.toString().replaceAll("]","").split("\\s");
//
//        System.out.println(oneList);
//        System.out.println(oneword[1]);
//        System.out.println(morphology.getNormalForms(chasticaOne).get(0));
//
//        System.out.println(twoList);
//        System.out.println(twoword[1]);
//        System.out.println(morphology.getNormalForms(chasticaTwo).get(0));
//
//        System.out.println(threeList);
//        System.out.println(threeword[1]);
//        System.out.println(morphology.getNormalForms(chasticaThree).get(0));



//        Document doc = null;
//        Elements urls = new Elements();
//        String dirtyText = "";
//
//        try {
//            doc = Jsoup.connect("https://agrostroiservice.ru/").get();
//            dirtyText = doc.text();
//
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//
//        System.out.println(dirtyText);
    }

