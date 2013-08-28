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

import com.google.common.collect.Multiset;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

public class Parser {

    private boolean headerPrinted;
    private final PrintWriter summary;
    private final PrintWriter checkSummary;

    public static void main(String[] args) throws IOException, InterruptedException {
        Shared.init(args);

        new File(Shared.resultsDir).mkdirs();

        Parser p = new Parser();
        SummaryData cikSummary = p.parsePages(Shared.pageDir, "root", "output-ciks.csv", true);
        SummaryData tikSummary = p.parsePages(Shared.pageDir, "first-", "output-tiks.csv", true);
        SummaryData uikSummary = p.parsePages(Shared.pageDir, "third-", "output-uiks.csv", false);

        p.checkSummaries(cikSummary, tikSummary, uikSummary);

//        p.parsePages(Shared.pageDir, "third-", "output-uiks.csv");
    }

    private void checkSummaries(SummaryData cik, SummaryData tik, SummaryData uik) {
        if (Shared.checkSummaries) {
            checkSummary.println(" --------------- CHECKING DIFFERENCE BETWEEN 'CIK' AND 'TIK' -------------------");
            summaryCompare(cik, tik);
            checkSummary.println();

            checkSummary.println(" --------------- CHECKING DIFFERENCE BETWEEN 'CIK' AND 'UIK' -------------------");
            summaryCompare(cik, uik);
            checkSummary.println();

            checkSummary.println(" --------------- CHECKING DIFFERENCE BETWEEN 'TIK' AND 'UIK' -------------------");
            summaryCompare(tik, uik);
            checkSummary.println();
        }
    }
    
    private void summaryCompare(SummaryData summ1, SummaryData summ2) {
        HashSet<List<String>> geos = new HashSet<List<String>>();
        geos.addAll(summ1.keys());
        geos.retainAll(summ2.keys());

        for (List<String> geo : geos) {
            Multiset<String> val1 = summ1.get(geo);
            Multiset<String> val2 = summ2.get(geo);

            Collection<String> metrics = new TreeSet<String>();
            metrics.addAll(val1.elementSet());
            metrics.addAll(val2.elementSet());

            if (!val1.equals(val2)) {
                checkSummary.printf("Found mismatches in aggregates over %s:\n", geo);
                for (String key : metrics) {
                    Integer v1 = val1.count(key);
                    Integer v2 = val2.count(key);

                    if (!v1.equals(v2)) {
                        checkSummary.printf(" {%9d} vs {%9d} [%4.1f%%]: %s\n", v1, v2, (v1 * 100.0 / v2 - 100), key);
                    }
                }
                checkSummary.println();
            }

            checkSummary.flush();
        }

    }

    public Parser() throws FileNotFoundException, UnsupportedEncodingException {
        summary = new PrintWriter(Shared.resultsDir + "/" + "summary.log", "UTF-8");
        checkSummary = new PrintWriter(Shared.resultsDir + "/" + "checkSummary.log", "UTF-8");
    }

    private SummaryData parsePages(String dataPath, final String input, String output, boolean parseLast) throws IOException {
        headerPrinted = false;

        String[] files = new File(dataPath).list(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.contains(input);
            }
        });

        Arrays.sort(files);

        TableData overall = new TableData();

        System.err.printf("Parsing %s to %s: ", dataPath + input, output);
        for (String dataFile : files) {
            System.err.printf(".");
            try {
                TableData data = parseUIK(Jsoup.parse(new File(dataPath + "/" + dataFile), "utf8"));
                overall.merge(data);

            } catch (IllegalStateException e) {
                System.err.printf(" (error in %s: %s) ", dataFile, e.getMessage());
            }
        }

        PrintWriter pw = new PrintWriter(new File(Shared.resultsDir + "/" + output), "UTF-8");
        emit(overall, pw);
        pw.flush();
        pw.close();

        System.err.printf("\n");

        Set<List<String>> args = new HashSet<List<String>>();

        if (Shared.checkSummaries) {
            for (Geography g :  overall.getGeographies()) {
                for (int c = 0; c < (g.size() + (parseLast ? 1 : 0)); c++) {
                    List<String> arg = new ArrayList<String>();
                    for (int t = 0; t < c; t++) {
                        arg.add(g.get(t));
                    }
                    args.add(arg);
                }
            }
        } else {
            args.add(Collections.<String>emptyList());
        }

        SummaryData summaryData = new SummaryData();

        for (List<String> arg : args) {
            summaryData.add(arg, overall.aggregate(arg));
        }
        
        for (List<String> arg : args) {
            summary.printf("Summary for %s (aggregate over %s):\n", output, arg.toString());
            Multiset<String> set = summaryData.get(arg);
            for (String s : set.elementSet()) {
                summary.printf("%15d : %s\n", set.count(s), s);
            }
            summary.printf("\n");
            summary.flush();
        }

        return summaryData;
    }

    public static class SummaryData {
        
        private final Map<List<String>, Multiset<String>> map;
        
        public SummaryData() {
            this.map = new HashMap<List<String>, Multiset<String>>();
        }
        
        public void add(List<String> coords, Multiset<String> e) {
            map.put(coords, e);
        }

        public Multiset<String> get(List<String> arg) {
            return map.get(arg);
        }

        public Set<List<String>> keys() {
            return map.keySet();
        }
    }
    
    private void emit(TableData data, PrintWriter pw) {
        Pattern pattern = Pattern.compile("[\"]");

        int COORD_MAX = 0;
        for (Geography g : data.getGeographies()) {
            COORD_MAX = Math.max(COORD_MAX, g.size());
        }
        
        Collection<String> metrics = data.getMetrics();

        if (!headerPrinted) {
            headerPrinted = true;
            for (int c = 0; c < COORD_MAX - 1; c++) {
                pw.print("\"Координата " + (c+1) + "\", ");
            }
            pw.print("\"ИК\""); // compensate for UIK name

            for (String name : metrics) {
                pw.print(",\"");
                pw.print(pattern.matcher(name).replaceAll(""));
                pw.print("\"");
            }
            pw.println();
        }

        for (Map.Entry<Geography, Multiset<String>> entry : data.asMap().entrySet()) {
            Geography g = entry.getKey();
            for (int c = 0; c < COORD_MAX - 1; c++) {
                pw.print("\"");
                pw.print(pattern.matcher(g.get(c)).replaceAll(""));
                pw.print("\",");
            }
            pw.print("\"");
            pw.print(pattern.matcher(g.get(COORD_MAX - 1)).replaceAll(""));
            pw.print("\"");
            for (String k : metrics) {
                pw.print(",");
                pw.print(entry.getValue().count(k));
            }
            pw.println();
        }

        pw.flush();
    }

    private TableData parseUIK(Document document) {

        Elements descripts = document.select("html > body > table > tbody > tr > td > table > tbody > tr > td > table > tbody > *");
        Elements data = document.select("html > body > table > tbody > tr > td > table > tbody > tr > td > div > table > tbody > tr");

        /*
         * Это названия строк
         */
        List<String> rowNames = new ArrayList<String>();
        List<String> rowSums = new ArrayList<String>();
        for (Element element : descripts) {
            Elements tds = element.children();
            if (tds.size() >= 3) {
                String text = tds.get(1).text();
                if (!text.contains("ИЗБИРАТЕЛЬНАЯ")) {
                    rowNames.add(text);
                }
                rowSums.add(tds.get(2).text());
            }
        }

        /**
         * Упс, пустая страница?
         * Отдельные мудни обернули таблицу ссылкой, попробуем ещё раз.
         */
        if (rowNames.isEmpty()) {

            descripts = document.select("html > body > a > table > tbody > tr > td > table > tbody > tr > td > table > tbody > tr");
            data = document.select("html > body > a > table > tbody > tr > td > table > tbody > tr > td > div > table > tbody > tr");

            /*
             * Это названия строк
             */
            rowNames.clear();
            rowSums.clear();
            for (Element element : descripts) {
                Elements tds = element.children();
                if (tds.size() >= 3) {
                    String text = tds.get(1).text();
                    if (!text.contains("ИЗБИРАТЕЛЬНАЯ")) {
                        rowNames.add(text);
                    }
                    rowSums.add(tds.get(2).text());
                }
            }
        }

        /**
         * Нипалучилось.
         */
        if (rowNames.isEmpty()) {
            System.err.println(descripts.toString());
            throw new IllegalStateException("Row names are empty");
        }

        /**
         * Строим координаты УИКа: они указаны линками вверху страницы.
         * Все координаты должны поместиться в $COORD_MAX
         */
        final int COORD_MAX = 5;

        List<String> coords = new ArrayList<String>(COORD_MAX);
        Elements links = document.select("html > body > table > tbody > tr > td > a");

        for (Element e : links) {
            if (e.attr("href").contains("region")) {
                coords.add(e.ownText());
            }
        }

        if (coords.size() == 0) {
            // second try with the link
            links = document.select("html > body > a > table > tbody > tr > td > a");

            for (Element e : links) {
                if (e.attr("href").contains("region")) {
                    coords.add(e.ownText());
                }
            }

            if (coords.size() == 0) {
                throw new IllegalStateException("Got some wrong coordinates: " + links);
            }
        }
        Geography g = new Geography(coords);

        /**
         * Данные: Map<"ИмяУИКа", Map<"Название строчки", число>>
         */
        TableData allData = new TableData();

        /**
         * Нет данных, только суммы, попробуем их отпарсить.
         */
        if (data.isEmpty()) {
            for (int i = 0, rowSumsSize = rowSums.size(); i < rowSumsSize; i++) {
                String text = rowSums.get(i);

                // спасибо деду за строчки типа "125 (35%)"!
                System.err.println(text);
                if (!text.trim().isEmpty()) {
                    int value = Integer.valueOf(text.split(" ")[0]);
                    allData.add(g.aid("Сумма"), rowNames.get(i), value);
                }
            }
        } else {

            /**
             * Это названия столбцов в таблице.
             * Обычно это названия УИКов.
             */
            List<String> uikNames = new ArrayList<String>();
            for (Element element : data.get(0).children()) {
                uikNames.add(element.text());
            }

            /**
             * Построчно парсим и пытаемся преобразовать в числа.
             */
            int curName = 0;
            for (int c = 1; c < data.size(); c++) {
                Elements children = data.get(c).children();

                boolean excCaught = false;
                for (int j = 0; j < children.size(); j++) {
                    String text = children.get(j).text();
                    try {
                        // спасибо деду за строчки типа "125 (35%)"!
                        int value = Integer.valueOf(text.split(" ")[0]);

                        String uikName = uikNames.get(j);
                        allData.add(g.aid(uikName), rowNames.get(curName), value);

                    } catch (NumberFormatException _) {
                        excCaught = true;
                    }
                }

                // хак: если строчка не была пустой, то переходим к следующему имени
                if (!excCaught) {
                    curName++;
                }
            }

        }

        return allData;
    }

    public static class TableData {

        public final Map<Geography, Multiset<String>> data;
        
        public TableData() {
            this.data = new TreeMap<Geography, Multiset<String>>();
        }

        public void add(Geography g, String label, int value) {
            Multiset<String> map = data.get(g);
            if (map == null) {
                map = new SortedMultiset<String>();
                data.put(g, map);
            }

            map.setCount(label, value);
        }

        public Collection<Geography> getGeographies() {
            return data.keySet();
        }

        public Collection<String> getMetrics() {
            Set<String> metrics = new TreeSet<String>();
            for (Multiset<String> set : data.values()) {
                metrics.addAll(set.elementSet());
            }
            return metrics;
        }

        public Map<Geography, Multiset<String>> asMap() {
            return data;
        }

        public void merge(TableData data) {
            for (Map.Entry<Geography, Multiset<String>> entry : data.asMap().entrySet()) {
                Multiset<String> set = entry.getValue();
                for (String s : set.elementSet()) {
                    add(entry.getKey(), s, set.count(s));
                }
            }
        }

        public Multiset<String> aggregate(List<String> coords) {
            Multiset<String> result = new SortedMultiset<String>();
            for (Map.Entry<Geography, Multiset<String>> entry: data.entrySet()) {
                Geography g = entry.getKey();
                
                boolean match = true;
                for(int c = 0; c < coords.size(); c++) {
                    match &= g.get(c).equalsIgnoreCase(coords.get(c));
                }
                
                if (match) {
                    Multiset<String> set = entry.getValue();
                    for (String k : set.elementSet()) {
                        result.add(k, set.count(k));
                    }
                    
                }
            }
            return result;
        }
    }

}
