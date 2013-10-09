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

    public static void main(String[] args) throws IOException, InterruptedException {
        Shared.init(args);

        new File(Shared.resultsDir).mkdirs();

        Parser p = new Parser();
        SummaryData cikSummary = p.parsePages(Shared.pageDir, "root", "output-ciks.csv", true);
        SummaryData tikSummary = p.parsePages(Shared.pageDir, "first-", "output-tiks.csv", true);
        SummaryData uikSummary = p.parsePages(Shared.pageDir, "second-", "output-uiks.csv", true);
        if (uikSummary.keys().isEmpty()) {
            uikSummary = p.parsePages(Shared.pageDir, "third-", "output-uiks.csv", false);
        }

        PrintWriter pw = new PrintWriter(System.out);
        pw.println();

        p.printSummaries(pw, "CIK", cikSummary, Collections.<String>emptyList());
        p.printSummaries(pw, "TIK", tikSummary, Collections.<String>emptyList());
        p.printSummaries(pw, "UIK", uikSummary, Collections.<String>emptyList());

        p.checkSummaries(pw, cikSummary, tikSummary, uikSummary);

        pw = new PrintWriter(Shared.resultsDir + "/" + "summary.log", "UTF-8");
        p.printSummaries(pw, "CIK", cikSummary, Collections.<String>emptyList());
        p.printSummaries(pw, "TIK", tikSummary, Collections.<String>emptyList());
        p.printSummaries(pw, "UIK", uikSummary, Collections.<String>emptyList());
        pw.close();

        pw = new PrintWriter(Shared.resultsDir + "/" + "checkSummary.log", "UTF-8");
        p.checkSummaries(pw, cikSummary, tikSummary, uikSummary);
        pw.close();
    }

    private void printSummaries(PrintWriter pw, String label, SummaryData data, List<String> key) {
        pw.printf("**** Summary for %s (aggregate over %s):\n", label, key.toString());
        Multiset<Metric> set = data.get(key);
        if (set == null || set.isEmpty()) {
            pw.println("No data.");
        } else {
            for (Metric s : set.elementSet()) {
                pw.printf("%15d : %s\n", set.count(s), s.getLabel());
            }
        }
        pw.printf("\n");
        pw.flush();
    }

    private void checkSummaries(PrintWriter pw, SummaryData cik, SummaryData tik, SummaryData uik) {
        if (Shared.checkSummaries) {
            pw.println("**** Checking totals between 'CIK' and 'TIK':");
            summaryCompare(pw, cik, tik);
            pw.println();

            pw.println("**** Checking totals between 'CIK' and 'UIK':");
            summaryCompare(pw, cik, uik);
            pw.println();

            pw.println("**** Checking totals between 'TIK' and 'UIK':");
            summaryCompare(pw, tik, uik);
            pw.println();
        }
        pw.flush();
    }
    
    private void summaryCompare(PrintWriter pw, SummaryData summ1, SummaryData summ2) {
        HashSet<List<String>> geos = new HashSet<List<String>>();
        geos.addAll(summ1.keys());
        geos.retainAll(summ2.keys());

        boolean foundAnomalies = false;
        for (List<String> geo : geos) {
            Multiset<Metric> val1 = summ1.get(geo);
            Multiset<Metric> val2 = summ2.get(geo);

            Collection<Metric> metrics = new TreeSet<Metric>();
            metrics.addAll(val1.elementSet());
            metrics.addAll(val2.elementSet());

            if (!val1.equals(val2)) {
                foundAnomalies = true;
                pw.printf("Found mismatches in aggregates over %s:\n", geo);
                for (Metric key : metrics) {
                    Integer v1 = val1.count(key);
                    Integer v2 = val2.count(key);

                    if (!v1.equals(v2)) {
                        pw.printf(" {%9d} vs {%9d} [%4.1f%%]: %s\n", v1, v2, (v1 * 100.0 / v2 - 100), key);
                    }
                }
                pw.println();
            }
        }

        if (!foundAnomalies) {
            pw.println("No anomalies in data.");
        }

        pw.flush();
    }

    public Parser() throws FileNotFoundException, UnsupportedEncodingException {
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
        
        return summaryData;
    }

    public static class Metric implements Comparable<Metric> {

        private final int index;
        private final String label;

        public Metric(int index, String label) {
            this.index = index;
            this.label = label;
        }

        @Override
        public int compareTo(Metric o) {
            return Integer.valueOf(index).compareTo(o.index);
        }

        public String getLabel() {
            return label;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Metric metric = (Metric) o;

            if (index != metric.index) return false;

            return true;
        }

        @Override
        public int hashCode() {
            return index;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public static class SummaryData {
        
        private final Map<List<String>, Multiset<Metric>> map;
        
        public SummaryData() {
            this.map = new HashMap<List<String>, Multiset<Metric>>();
        }
        
        public void add(List<String> coords, Multiset<Metric> e) {
            map.put(coords, e);
        }

        public Multiset<Metric> get(List<String> arg) {
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
        
        Collection<Metric> metrics = data.getMetrics();

        if (!headerPrinted) {
            headerPrinted = true;
            for (int c = 0; c < COORD_MAX - 1; c++) {
                pw.print("\"Координата " + (c+1) + "\", ");
            }
            pw.print("\"ИК\""); // compensate for UIK name

            for (Metric name : metrics) {
                pw.print(",\"");
                pw.print(pattern.matcher(name.getLabel()).replaceAll(""));
                pw.print("\"");
            }
            pw.println();
        }

        for (Map.Entry<Geography, Multiset<Metric>> entry : data.asMap().entrySet()) {
            Geography g = entry.getKey();
            for (int c = 0; c < COORD_MAX - 1; c++) {
                pw.print("\"");
                pw.print(pattern.matcher(g.get(c)).replaceAll(""));
                pw.print("\",");
            }
            pw.print("\"");
            pw.print(pattern.matcher(g.get(COORD_MAX - 1)).replaceAll(""));
            pw.print("\"");
            for (Metric k : metrics) {
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
        List<Metric> rowNames = new ArrayList<Metric>();
        List<Metric> rowSums = new ArrayList<Metric>();

        int index = 0;
        for (Element element : descripts) {
            Elements tds = element.children();
            if (tds.size() >= 3) {
                String text = tds.get(1).text();
                if (!text.contains("ИЗБИРАТЕЛЬНАЯ")) {
                    rowNames.add(new Metric(index++, text));
                }
                rowSums.add(new Metric(index++, tds.get(2).text()));
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

            index = 0;
            for (Element element : descripts) {
                Elements tds = element.children();
                if (tds.size() >= 3) {
                    String text = tds.get(1).text();
                    if (!text.contains("ИЗБИРАТЕЛЬНАЯ")) {
                        rowNames.add(new Metric(index++, text));
                    }
                    rowSums.add(new Metric(index++, tds.get(2).text()));
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
                String text = rowSums.get(i).getLabel();

                // спасибо деду за строчки типа "125 (35%)"!
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

        public final Map<Geography, Multiset<Metric>> data;
        
        public TableData() {
            this.data = new TreeMap<Geography, Multiset<Metric>>();
        }

        public void add(Geography g, Metric label, int value) {
            Multiset<Metric> map = data.get(g);
            if (map == null) {
                map = new SortedMultiset<Metric>();
                data.put(g, map);
            }

            map.setCount(label, value);
        }

        public Collection<Geography> getGeographies() {
            return data.keySet();
        }

        public Collection<Metric> getMetrics() {
            Set<Metric> metrics = new TreeSet<Metric>();
            for (Multiset<Metric> set : data.values()) {
                metrics.addAll(set.elementSet());
            }
            return metrics;
        }

        public Map<Geography, Multiset<Metric>> asMap() {
            return data;
        }

        public void merge(TableData data) {
            for (Map.Entry<Geography, Multiset<Metric>> entry : data.asMap().entrySet()) {
                Multiset<Metric> set = entry.getValue();
                for (Metric s : set.elementSet()) {
                    add(entry.getKey(), s, set.count(s));
                }
            }
        }

        public Multiset<Metric> aggregate(List<String> coords) {
            Multiset<Metric> result = new SortedMultiset<Metric>();
            for (Map.Entry<Geography, Multiset<Metric>> entry: data.entrySet()) {
                Geography g = entry.getKey();
                
                boolean match = true;
                for(int c = 0; c < coords.size(); c++) {
                    match &= g.get(c).equalsIgnoreCase(coords.get(c));
                }
                
                if (match) {
                    Multiset<Metric> set = entry.getValue();
                    for (Metric k : set.elementSet()) {
                        result.add(k, set.count(k));
                    }
                    
                }
            }
            return result;
        }
    }

}
