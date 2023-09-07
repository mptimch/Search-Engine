package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.Page;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public interface IndexRepository extends JpaRepository <Index, Integer> {

    long countPagesByLemma (Lemma lemma);

    @Query("SELECT DISTINCT i.page FROM Index i WHERE i.lemma = :lemma")
    List<Page> findDistinctPagesByLemma(Lemma lemma);

    @Query("SELECT DISTINCT i.page FROM Index i WHERE i.lemma = :lemma AND i.page IN :pages")
    Optional<List<Page>> findDistinctPageByLemmaAndPageIn(@Param("lemma") Lemma lemma, @Param("pages") List<Page> pages);


    @Query("SELECT COUNT(i) FROM Index i WHERE i.page = :page AND i.lemma IN :lemmas")
    long countOccurrencesOnPage(@Param("page") Page page, @Param("lemmas") List<Lemma> lemmas);
}
