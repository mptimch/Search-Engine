package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import searchengine.model.Page;
import searchengine.model.Site;


public interface PageRepository extends JpaRepository <Page, Integer> {

    boolean existsByPathAndSite(String path, Site site);

    Page findByPathAndSite(String path, Site site);

    Integer countPagesBySiteId (Integer siteId);

    long count();

}
