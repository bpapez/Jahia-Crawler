/**
 * This file is part of Jahia, next-generation open source CMS:
 * Jahia's next-generation, open source CMS stems from a widely acknowledged vision
 * of enterprise application convergence - web, search, document, social and portal -
 * unified by the simplicity of web content management.
 *
 * For more information, please visit http://www.jahia.com.
 *
 * Copyright (C) 2002-2011 Jahia Solutions Group SA. All rights reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *
 * As a special exception to the terms and conditions of version 2.0 of
 * the GPL (or any later version), you may redistribute this Program in connection
 * with Free/Libre and Open Source Software ("FLOSS") applications as described
 * in Jahia's FLOSS exception. You should have received a copy of the text
 * describing the FLOSS exception, and it is also available here:
 * http://www.jahia.com/license
 *
 * Commercial and Supported Versions of the program (dual licensing):
 * alternatively, commercial and supported versions of the program may be used
 * in accordance with the terms and conditions contained in a separate
 * written agreement between you and Jahia Solutions Group SA.
 *
 * If you are unsure which license is appropriate for your use,
 * please contact the sales department at sales@jahia.com.
 */

package org.jahia.modules.crawl.taglib;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import javax.servlet.jsp.JspException;
import javax.servlet.jsp.PageContext;

import org.apache.hadoop.conf.Configuration;
import org.apache.nutch.searcher.HitDetails;
import org.apache.nutch.searcher.Hits;
import org.apache.nutch.searcher.NutchBean;
import org.apache.nutch.searcher.Query;
import org.jahia.modules.crawl.CrawlDBUtil;
import org.jahia.modules.crawl.NutchHit;
import org.jahia.services.SpringContextSingleton;
import org.jahia.services.render.RenderContext;
import org.jahia.services.search.Hit;
import org.jahia.services.search.SearchCriteria;
import org.jahia.services.search.SearchCriteriaFactory;
import org.jahia.taglibs.AbstractJahiaTag;

/**
 * Performs the content search and exposes search results for being displayed.
 * 
 * @author Sergiy Shyrkov
 */
public class ResultsTag extends AbstractJahiaTag {

    private static final long serialVersionUID = 2848686280888802590L;

    private String countVar;

    private List<Hit<?>> hits;

    private String searchCriteriaBeanName;

    private String searchCriteriaVar;

    private String termVar;

    private String var;

    @Override
    public int doEndTag() throws JspException {
        pageContext.removeAttribute(getVar(), PageContext.PAGE_SCOPE);
        pageContext.removeAttribute(getCountVar(), PageContext.PAGE_SCOPE);
        pageContext.removeAttribute(getSearchCriteriaVar(), PageContext.PAGE_SCOPE);
        pageContext.removeAttribute(getTermVar(), PageContext.PAGE_SCOPE);
        resetState();

        return EVAL_PAGE;
    }

    @Override
    public int doStartTag() throws JspException {

        try {
            RenderContext renderContext = getRenderContext();
            SearchCriteria criteria = getSearchCriteria(renderContext);

            if (null == criteria) {
                return SKIP_BODY;
            }
            Configuration conf = CrawlDBUtil.createConfiguration();
            NutchBean bean = new NutchBean(conf);
            Query query = Query.parse(criteria.getTerms().get(0).getTerm(), conf);
            Properties crawlerProperties = (Properties) SpringContextSingleton.getBeanInModulesContext("crawlerProperties");
            int numHits = (int)((criteria.getOffset() > 0 || criteria.getLimit() > 0) ? (criteria.getOffset() + 1) * criteria.getLimit()
                    : Integer.parseInt(crawlerProperties.getProperty("resultFetchSize")));  

            Hits nutchHits = bean.search(query, numHits);
            if (nutchHits.getLength() > criteria.getOffset()) {
                hits = convertToJahiaHits(bean, nutchHits.getHits((int)criteria.getOffset(), nutchHits.getLength()), query, renderContext);
            } else {
                hits = Collections.emptyList();
            }
            int count = hits.size();

            pageContext.setAttribute(getVar(), hits);
            pageContext.setAttribute(getCountVar(), Integer.valueOf(count));
            pageContext.setAttribute(getSearchCriteriaVar(), criteria);
            if (!criteria.getTerms().isEmpty() && !criteria.getTerms().get(0).isEmpty()) {
                pageContext.setAttribute(getTermVar(), criteria.getTerms().get(0).getTerm());
            }
        } catch (IOException e) {
            throw new JspException(e);
        }

        return EVAL_BODY_INCLUDE;
    }

    /**
     * Returns the default name of the <code>countVar</code> variable if not provided.
     * 
     * @return the default name of the <code>countVar</code> variable if not provided
     */
    private String getCountVar() {
        return countVar != null ? countVar : getDefaultCountVarName();
    }

    protected String getDefaultCountVarName() {
        return "count";
    }

    protected String getDefaultSearchCriteriaVarName() {
        return "searchCriteria";
    }

    protected String getDefaultTermVarName() {
        return "term";
    }

    /**
     * Returns the default name of the <code>var</code> variable if not provided.
     * 
     * @return the default name of the <code>var</code> variable if not provided
     */
    protected String getDefaultVarName() {
        return "hits";
    }

    /**
     * Returns a list of {@link Hit} objects that are results of the query.
     * 
     * @return a list of {@link Hit} objects that are results of the query
     */
    public List<Hit<?>> getHits() {
        return hits;
    }

    /**
     * Obtains the {@link SearchCriteria} bean to execute the search with.
     * 
     * @param ctx
     *            current rendering context
     * @return the {@link SearchCriteria} bean to execute the search with
     */
    protected SearchCriteria getSearchCriteria(RenderContext ctx) {
        return searchCriteriaBeanName != null ? (SearchCriteria) pageContext.getAttribute(searchCriteriaBeanName) : SearchCriteriaFactory
                .getInstance(ctx);
    }

    private String getSearchCriteriaVar() {
        return searchCriteriaVar != null ? searchCriteriaVar : getDefaultSearchCriteriaVarName();
    }

    private String getTermVar() {
        return termVar != null ? termVar : getDefaultTermVarName();
    }

    private String getVar() {
        return var != null ? var : getDefaultVarName();
    }

    @Override
    protected void resetState() {
        var = null;
        countVar = null;
        hits = null;
        searchCriteriaBeanName = null;
        searchCriteriaVar = null;
        termVar = null;
        super.resetState();
    }

    public void setCountVar(String countVar) {
        this.countVar = countVar;
    }

    public void setSearchCriteriaBeanName(String searchCriteriaBeanName) {
        this.searchCriteriaBeanName = searchCriteriaBeanName;
    }

    public void setSearchCriteriaVar(String searchCriteriaVar) {
        this.searchCriteriaVar = searchCriteriaVar;
    }

    public void setTermVar(String termVar) {
        this.termVar = termVar;
    }

    public void setVar(String var) {
        this.var = var;
    }

    private List<Hit<?>> convertToJahiaHits(NutchBean bean, org.apache.nutch.searcher.Hit[] nutchHits, Query query,
            RenderContext renderContext) throws IOException {
        List<Hit<?>> hits = new ArrayList<Hit<?>>();
        for (HitDetails details : bean.getDetails(nutchHits)) {
            NutchHit hit = new NutchHit(details, renderContext);
            hit.setExcerpt(bean.getSummary(details, query).toHtml(true));
            hits.add(hit);
        }
        return hits;
    }

}
