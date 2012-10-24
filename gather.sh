#!/bin/sh

# PARLIAMENT ELECTIONS, 2011
#URL="http://www.vybory.izbirkom.ru/region/region/izbirkom?action=show&root=1&tvd=100100028713304&vrn=100100028713299&region=0&global=1&sub_region=0&prver=0&pronetvd=null&vibid=100100028713304&type=233"

# PRESIDENT ELECTIONS, 2012
URL="http://www.vybory.izbirkom.ru/region/region/izbirkom?action=show&root=1&tvd=100100031793509&vrn=100100031793505&region=0&global=1&sub_region=0&prver=0&pronetvd=null&vibid=100100031793509&type=227"

while true; do
    DATE=$(date +%Y%m%d-%H%M%S)
    java -Dhttp.agent="CIKRF Spider (Java); please report abuse to IP owner;" -jar cikrf-spider.jar -r "$URL" -p cikrf-raw-$DATE/
    java -jar cikrf-parser.jar -p cikrf-raw-$DATE/ -o cikrf-csv-$DATE/ -c
    tar czf cikrf-raw-$DATE.tar.gz cikrf-raw-$DATE/
    tar czf cikrf-csv-$DATE.tar.gz cikrf-csv-$DATE/
    rm -rf cikrf-raw-$DATE/
    rm -rf cikrf-csv-$DATE/
    mv cikrf-raw-$DATE.tar.gz ../public_html/articles/elections2012/cik-r1/raw/
    mv cikrf-csv-$DATE.tar.gz ../public_html/articles/elections2012/cik-r1/csv/
    sleep 40000
done
