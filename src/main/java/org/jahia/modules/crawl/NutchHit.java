package org.jahia.modules.crawl;

import java.util.Date;

import org.apache.nutch.searcher.Hit;
import org.apache.nutch.searcher.HitDetails;
import org.jahia.services.render.RenderContext;
import org.jahia.services.search.AbstractHit;

public class NutchHit extends AbstractHit<HitDetails> {

    public NutchHit(HitDetails resource, RenderContext context) {
        super(resource, context);
    }

    public String getContentType() {
        // TODO Auto-generated method stub
        return null;
    }

    public Date getCreated() {
        // TODO Auto-generated method stub
        return null;
    }

    public String getCreatedBy() {
        // TODO Auto-generated method stub
        return null;
    }

    public Date getLastModified() {
        // TODO Auto-generated method stub
        return null;
    }

    public String getLastModifiedBy() {
        // TODO Auto-generated method stub
        return null;
    }

    public String getLink() {
        return resource.getValue("url");
    }

    public String getTitle() {
        return resource.getValue("title");
    }

    public String getType() {
        // TODO Auto-generated method stub
        return null;
    }

}
