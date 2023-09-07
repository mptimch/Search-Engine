package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import searchengine.model.Lemma;
import searchengine.model.Site;

import java.util.List;
import java.util.Optional;

public interface LemmaRepository extends JpaRepository <Lemma, Integer> {

    Optional <Lemma> findLemmaByLemma(String lemma);

    Integer countLemmaBySiteId (Integer siteId);

    long count();

    Optional <Lemma> findLemmaByLemmaAndSite (String lemma, Site site);

    @Query("SELECT l.lemma FROM Lemma l WHERE l IN :lemmas")
    List<String> findLemmasByLemmaIn(List<Lemma> lemmas);
}
