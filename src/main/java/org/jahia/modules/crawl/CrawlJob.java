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

package org.jahia.modules.crawl;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.mapred.JobConf;
import org.apache.log4j.Logger;
import org.apache.nutch.crawl.CrawlDb;
import org.apache.nutch.crawl.Generator;
import org.apache.nutch.crawl.Injector;
import org.apache.nutch.crawl.LinkDb;
import org.apache.nutch.fetcher.Fetcher;
import org.apache.nutch.indexer.DeleteDuplicates;
import org.apache.nutch.indexer.IndexMerger;
import org.apache.nutch.indexer.Indexer;
import org.apache.nutch.parse.ParseSegment;
import org.apache.nutch.util.HadoopFSUtil;
import org.apache.nutch.util.NutchJob;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.scheduling.quartz.QuartzJobBean;

/**
 * Basic crawl service
 * 
 * @author nutch-dev <nutch-dev at lucene.apache.org> and Benjamin Papez
 */

public class CrawlJob extends QuartzJobBean {
    private static Logger logger = Logger.getLogger(CrawlJob.class);

    private final static Path baseDir = new Path(
            System.getProperty("java.io.tmpdir")
                    + (!(System.getProperty("java.io.tmpdir").endsWith("/") || System.getProperty("java.io.tmpdir").endsWith("\\")) ? System
                            .getProperty("file.separator") : "") + "nutch/jahia");

    private Configuration conf;

    private FileSystem fs;

    private Path crawldbPath;
    private Path segmentsPath;
    private Path urlPath;
    private Path linkDb;
    private Path segments;
    private Path indexes;
    private Path index;
    
    public void init() {
        try {
            conf = CrawlDBUtil.createConfiguration();
            fs = FileSystem.get(conf);
            urlPath = new Path(baseDir, "urls");
            crawldbPath = new Path(baseDir, "crawldb");
            segmentsPath = new Path(baseDir, "segments");
            linkDb = new Path(baseDir + "/linkdb");
            segments = new Path(baseDir + "/segments");
            indexes = new Path(baseDir + "/indexes");
            index = new Path(baseDir + "/index");
        } catch (Exception ex) {
            logger.warn("Exception during test setUp", ex);
        }
    }

    private static String getDate() {
        return new SimpleDateFormat("yyyyMMddHHmmss").format(new Date(System.currentTimeMillis()));
    }

    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        if (conf == null) {
            init();
        }
        try {
            JobDataMap mergedJobDataMap = context.getMergedJobDataMap();
            List<String> urls = (List<String>) mergedJobDataMap.get("urls");
            
            JobConf job = new NutchJob(conf);
            
            Path tmpDir = job.getLocalPath("crawl" + Path.SEPARATOR + getDate());

            CrawlDBUtil.generateSeedList(fs, urlPath, urls);
            // inject
            Injector injector = new Injector(conf);
            injector.inject(crawldbPath, urlPath);

            // generate
            Generator g = new Generator(conf);
            // fetch
            conf.setBoolean("fetcher.parse", true);
            Fetcher fetcher = new Fetcher(conf);
            ParseSegment parseSegment = new ParseSegment(conf);
            CrawlDb crawlDbTool = new CrawlDb(conf);

            int depth = 5;
            int threads = 4;
            int i;
            for (i = 0; i < depth; i++) { // generate new segment
                Path generatedSegment = g.generate(crawldbPath, segmentsPath, 1, Long.MAX_VALUE, Long.MAX_VALUE, false, false);

                if (generatedSegment == null) {
                    logger.info("Stopping at depth=" + i + " - no more URLs to fetch.");
                    break;
                }
                fetcher.fetch(generatedSegment, threads, true);
                if (!Fetcher.isParsing(job)) {
                    parseSegment.parse(generatedSegment); // parse it, if needed
                }
                crawlDbTool.update(crawldbPath, new Path[] { generatedSegment }, true, true);
            }
            if (i > 0) {
                LinkDb linkDbTool = new LinkDb(conf);
                Indexer indexer = new Indexer(conf);
                DeleteDuplicates dedup = new DeleteDuplicates(conf);
                IndexMerger merger = new IndexMerger(conf);

                linkDbTool.invert(linkDb, segments, true, true, false); // invert links

                if (indexes != null) {
                    // Delete old indexes
                    if (fs.exists(indexes)) {
                        logger.info("Deleting old indexes: " + indexes);
                        fs.delete(indexes, true);
                    }

                    // Delete old index
                    if (fs.exists(index)) {
                        logger.info("Deleting old merged index: " + index);
                        fs.delete(index, true);
                    }
                }

                // index, dedup & merge
                FileStatus[] fstats = fs.listStatus(segments, HadoopFSUtil.getPassDirectoriesFilter(fs));
                indexer.index(indexes, crawldbPath, linkDb, Arrays.asList(HadoopFSUtil.getPaths(fstats)));
                if (indexes != null) {
                    dedup.dedup(new Path[] { indexes });
                    fstats = fs.listStatus(indexes, HadoopFSUtil.getPassDirectoriesFilter(fs));
                    merger.merge(HadoopFSUtil.getPaths(fstats), index, tmpDir);
                }
            } else {
                logger.warn("No URLs to fetch - check your seed list and URL filters.");
            }

        } catch (IOException e) {
            logger.error("Exception while crawling", e);
        }
    }

}
