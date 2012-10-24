/**
 Copyright 2012 Aleksey Shipilev

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */

package net.shipilev.elections.cikrf;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;

public class Spider {

    public static void main(String[] args) throws IOException, InterruptedException {
        Shared.init(args);

        Spider d = new Spider();
        d.downloadAll();
    }

    private void writeFile(Document document, String filename) throws IOException {
        PrintWriter writer = new PrintWriter(filename, "UTF-8");
        writer.write(document.toString());
        writer.flush();
        writer.close();
    }

    private void downloadAll() throws IOException, InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(Shared.threads, new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r);
                t.setDaemon(true);
                return t;
            }
        });

        new File(Shared.pageDir).mkdirs();

        PrintWriter pw = new PrintWriter(System.err, true);

        pw.println("Downloading tree from " + Shared.rootURL + " to " + Shared.pageDir);

        Document root = Shared.download(Shared.rootURL);

        writeFile(root, Shared.pageDir + "/root.data.html");

        List<String> firstLevel = new ArrayList<String>();
        List<String> secondLevel = new ArrayList<String>();
        List<String> thirdLevel = new ArrayList<String>();

        /**
         * Первый уровень: Федеральные? ИКи
         */
        Elements firstLevelLinks = root.select("html > body > table > tbody > tr > td > table > tbody > tr > td > div > table > tbody > tr > td > nobr > a[href]");
        for (Element link : firstLevelLinks) {
            firstLevel.add(link.attr("href"));
        }

        firstLevel = firstLevel.subList(0, Math.min(Shared.wideLimit, firstLevel.size()));

        pw.println(firstLevel.size() + " first-level pages to be downloaded");

        /**
         * Второй уровень: Сводки по ТИКам
         */
        List<Future<Document>> fFirstLevel = new ArrayList<Future<Document>>();
        for (String url : firstLevel) {
            fFirstLevel.add(executor.submit(new GetFileTask(url)));
        }

        int i = 0;
        for (Iterator<Future<Document>> iterator = fFirstLevel.iterator(); iterator.hasNext(); ) {
            Future<Document> fDoc = iterator.next();
            pw.printf("First-level download %d of %d, %d second-level pages \n", i + 1, firstLevel.size(), secondLevel.size());

            Document doc;
            try {
                doc = fDoc.get();
            } catch (ExecutionException e) {
                pw.println("Error downloading " + fDoc + " " + e);
                continue;
            }

            writeFile(doc, Shared.pageDir + "/first-" + i + ".data.html");

            Elements secondLevelLinks = doc.select("html > body > table > tbody > tr > td > table > tbody > tr > td > div > table > tbody > tr > td > nobr > a[href]");
            for (Element link : secondLevelLinks) {
                secondLevel.add(link.attr("href"));
            }

            /**
             * Shortcut для тех страниц, которые не имеют gateway-страниц (типа "Зарубежных территорий")
             */
            Elements uikLinks = doc.select("html > body > table > tbody > tr > td > a");
            for (Element link : uikLinks) {
                if (link.ownText().contains("сайт избирательной комиссии субъекта Российской Федерации")) {
                    thirdLevel.add(link.attr("href"));
                }
            }

            i++;
            iterator.remove();
        }
        fFirstLevel.clear();

        secondLevel = secondLevel.subList(0, Math.min(Shared.wideLimit, secondLevel.size()));

        pw.println(secondLevel.size() + " second-level pages to be downloaded");

        /**
         * Спасибо дядям из избиркома, нужно перейти на сайт конкретного ТИКа для получения подробностей по УИКам
         */
        List<Future<Document>> fSecondLevel = new ArrayList<Future<Document>>();
        for (String url : secondLevel) {
            fSecondLevel.add(executor.submit(new GetFileTask(url)));
        }

        i = 0;

        for (Iterator<Future<Document>> iterator = fSecondLevel.iterator(); iterator.hasNext(); ) {
            Future<Document> fDoc = iterator.next();
            pw.printf("Second-level download %d of %d, %d third-level pages\n", i + 1, secondLevel.size(), thirdLevel.size());

            Document doc;
            try {
                doc = fDoc.get();
            } catch (ExecutionException e) {
                pw.println("Error downloading " + fDoc + " " + e);
                continue;
            }

            writeFile(doc, Shared.pageDir + "/second-" + i + ".data.html");

            Elements uikLinks = doc.select("html > body > table > tbody > tr > td > a");
            for (Element link : uikLinks) {
                if (link.ownText().contains("сайт избирательной комиссии субъекта Российской Федерации")) {
                    thirdLevel.add(link.attr("href"));
                }
            }

            i++;
            iterator.remove();
        }
        fSecondLevel.clear();

        thirdLevel = thirdLevel.subList(0, Math.min(Shared.wideLimit, thirdLevel.size()));

        pw.println(thirdLevel.size() + " third-level pages to be downloaded");

        List<Future<Document>> fThirdLevel = new ArrayList<Future<Document>>();
        for (String url : thirdLevel) {
            fThirdLevel.add(executor.submit(new GetFileTask(url)));
        }

        i = 0;
        for (Iterator<Future<Document>> iterator = fThirdLevel.iterator(); iterator.hasNext(); ) {
            Future<Document> fDoc = iterator.next();
            pw.printf("Third-level download %d of %d\n", i + 1, thirdLevel.size());

            Document doc;
            try {
                doc = fDoc.get();
            } catch (ExecutionException e) {
                pw.println("Error downloading " + fDoc + " " + e);
                continue;
            }

            writeFile(doc, Shared.pageDir + "/third-" + i + ".data.html");

            i++;
            iterator.remove();
        }
        fThirdLevel.clear();
    }

    public static class GetFileTask implements Callable<Document> {

        private final String url;

        public GetFileTask(String url) {
            this.url = url;
        }

        @Override
        public Document call() throws Exception {
            return Shared.download(url);
        }

        @Override
        public String toString() {
            return url;
        }
    }

}
