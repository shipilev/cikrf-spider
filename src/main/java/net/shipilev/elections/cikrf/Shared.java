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

import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import org.jsoup.Jsoup;
import org.jsoup.helper.HttpConnection;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;

public class Shared {

    public static String rootURL;
    public static String pageDir;
    public static String resultsDir;
    public static Integer threads;
    public static Integer wideLimit;
    public static Integer thinkTime;
    public static boolean checkSummaries;

    public static void init(String[] args) throws IOException {
        OptionParser parser = new OptionParser() {
            {
                accepts("h", "Print help and exit");
                accepts("c", "Check summaries (long and tedious)");
            }
        };

        parser.formatHelpWith(new OptFormatter());

        OptionSpec<String> oRootURL = parser.accepts("r", "Root URL on www.cikrf.ru")
                .withRequiredArg().describedAs("DIR").ofType(String.class);

        OptionSpec<String> oPageDir = parser.accepts("p", "Page download dir. This will be populated by spider, and grabbed by parser.")
                .withRequiredArg().describedAs("DIR").ofType(String.class)
                .defaultsTo("download/");

        OptionSpec<String> oResultsDir = parser.accepts("o", "Result output dir. Parsers will write out CSVs there.")
                .withRequiredArg().describedAs("DIR").ofType(String.class)
                .defaultsTo("results/");

        OptionSpec<Integer> oThreads = parser.accepts("t", "Number of network threads.")
                .withRequiredArg().describedAs("threads").ofType(Integer.class)
                .defaultsTo(1);

        OptionSpec<Integer> oThinkTime = parser.accepts("s", "Time to sleep between network requests.")
                .withRequiredArg().describedAs("msecs").ofType(Integer.class)
                .defaultsTo(200);

        OptionSpec<Integer> oWideLimit = parser.accepts("w", "Limit number of pages on each level (useful for debugging, to skip downloading all the pages)")
                .withRequiredArg().describedAs("pages").ofType(Integer.class)
                .defaultsTo(Integer.MAX_VALUE);

        OptionSpec<Boolean> shouldCheck = parser.accepts("c", "Cross-check the data").withRequiredArg().ofType(boolean.class).defaultsTo(true);

        OptionSet set = null;
        try {
            set = parser.parse(args);
        } catch (OptionException e) {
            parser.printHelpOn(System.err);
            System.exit(1);
        }

        if (set.has("h")) {
            parser.printHelpOn(System.err);
            System.exit(0);
        }

        pageDir = set.valueOf(oPageDir);
        resultsDir = set.valueOf(oResultsDir);
        threads = set.valueOf(oThreads);
        wideLimit = set.valueOf(oWideLimit);
        rootURL = set.valueOf(oRootURL);
        thinkTime = set.valueOf(oThinkTime);
        checkSummaries = set.valueOf(shouldCheck);
    }

    public static Document download(String url) throws InterruptedException {
//        System.err.println("Downloading " + url);
        
        String doc = fetch(url);
//        System.err.println("1-st Received: " + doc);

        Integer key = parseLoginKey(doc);
        if (key != null) {
            sendPOST(url, key);
            doc = fetch(url);

//            System.err.println("2-nd Received: " + doc);
        }

        return Jsoup.parse(doc);
    }

    public static Integer parseLoginKey(String doc) {
        Document document = Jsoup.parse(doc);
        
        Elements elements = document.select("html > body > form > input");

        for (Element e : elements) {
            if ("key".equals(e.attr("name"))) {
                return Integer.valueOf(e.attr("value"));
            }
        }
        
        return null;
    }

    private static void sendPOST(String url, int key) {
//        System.err.println("Sending POST with " + key);

        URL u;
        HttpURLConnection connection = null;
        try {
            u = new URL(url);
            connection = (HttpURLConnection) u.openConnection();
            connection.setRequestMethod("POST");
            connection.setDoInput(true);
            connection.setDoOutput(true);

            DataOutputStream stream = new DataOutputStream(connection.getOutputStream());
            stream.writeBytes("key=" + key);
            stream.flush();
            stream.close();

        } catch (IOException e) {
            System.err.println("IOException while sending POST: " + e.getMessage());
        } finally {
            if (connection != null) {
                try {
                    readOut(connection.getInputStream());
                } catch (IOException e) {
                    // do nothing
                }

                try {
                    readOut(connection.getErrorStream());
                } catch (IOException e) {
                    // do nothing
                }
            }
        }
    }

    private static void readOut(InputStream stream) throws IOException {
        if (stream == null) {
            return;
        }
        BufferedReader in = new BufferedReader(new InputStreamReader(stream));
        String line;
        while((line=in.readLine())!=null) {
            // do nothing
        }
        in.close();
    }

    private static String fetch(String url) throws InterruptedException {

        URL u;
        HttpURLConnection hc = null;

        for (int tries = 0; tries < 10; tries++) {
            try {
                TimeUnit.MILLISECONDS.sleep(thinkTime);

                // God, I miss Java 7 here.

                u = new URL(url);
                hc = (HttpURLConnection) u.openConnection();
                hc.setDoInput(true);
                hc.setDoOutput(false);

                InputStream in;

                hc.setRequestProperty("Accept-Encoding", "gzip, deflate");
                hc.setRequestProperty("Pragma", "no-cache");
                if (hc.getResponseCode() == 200) {
                    in = hc.getInputStream();
                    if ("gzip".equals(hc.getHeaderField("Content-Encoding"))) {
                        in = new GZIPInputStream(in);
                    }
                } else {
                    throw new IOException(hc.getResponseCode() + " " + hc.getResponseMessage());
                }
                
                InputStreamReader streamReader = new InputStreamReader(in, "cp1251");
                BufferedReader reader = new BufferedReader(streamReader);

                StringBuilder builder = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    builder.append(line);
                }

                return builder.toString();
            } catch (IOException e) {
                System.err.println("Error fetching: " + e.getMessage());
                TimeUnit.SECONDS.sleep(5);
            } finally {
                    try {
                    readOut(hc.getInputStream());
                } catch (IOException e) {
                    // do nothing
                }

                    try {
                    readOut(hc.getErrorStream());
                } catch (IOException e) {
                    // do nothing
                }
            }
        }
        return "";
    }

}
