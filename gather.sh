#!/bin/sh

# PARLIAMENT ELECTIONS, 2011
#URL="http://www.vybory.izbirkom.ru/region/region/izbirkom?action=show&root=1&tvd=100100028713304&vrn=100100028713299&region=0&global=1&sub_region=0&prver=0&pronetvd=null&vibid=100100028713304&type=233"

# PRESIDENT ELECTIONS, 2012
#URL="http://www.vybory.izbirkom.ru/region/region/izbirkom?action=show&root=1&tvd=100100031793509&vrn=100100031793505&region=0&global=1&sub_region=0&prver=0&pronetvd=null&vibid=100100031793509&type=227"

# MOSCOW MAYOR ELECTIONS, 2003
URL1="http://www.moscow_city.vybory.izbirkom.ru/region/region/moscow_city?action=show&root=1&tvd=277200079460&vrn=277200079459&region=77&global=&sub_region=0&prver=0&pronetvd=null&vibid=277200079460&type=222"

# MOSCOW AREA MAYOR ELECTIONS, 2003
URL2="http://www.moscow_reg.vybory.izbirkom.ru/region/region/moscow_reg?action=show&root=1&tvd=250200072700&vrn=250200072699&region=50&global=&sub_region=0&prver=0&pronetvd=null&vibid=250200072700&type=222"

# MOSCOW MAYOR ELECTIONS, 2013
#URL1="http://www.moscow_city.vybory.izbirkom.ru/region/region/moscow_city?action=show&root=1&tvd=27720001368293&vrn=27720001368289&region=77&global=&sub_region=0&prver=0&pronetvd=null&vibid=27720001368293&type=234"

# MOSCOW AREA MAYOR ELECTIONS, 2013
#URL2="http://www.moscow_reg.vybory.izbirkom.ru/region/region/moscow_reg?action=show&root=1&tvd=75070001571771&vrn=75070001571767&region=50&global=&sub_region=0&prver=0&pronetvd=null&vibid=75070001571771&type=222"

mkdir -p data/moscow/ data/mo/

while true; do
    DATE=$(date +%Y%m%d-%H%M%S)
    java -Dhttp.agent="CIKRF Spider (Java); please report abuse to IP owner;" -jar cikrf-spider.jar -r "$URL1" -p cikrf-web-moscow-$DATE/
    java -Dhttp.agent="CIKRF Spider (Java); please report abuse to IP owner;" -jar cikrf-spider.jar -r "$URL2" -p cikrf-web-mo-$DATE/
    java -jar cikrf-parser.jar -p cikrf-web-moscow-$DATE/ -o cikrf-csv-moscow-$DATE/
    java -jar cikrf-parser.jar -p cikrf-web-mo-$DATE/     -o cikrf-csv-mo-$DATE/
    tar czf cikrf-web-moscow-$DATE.tar.gz cikrf-web-moscow-$DATE/
    tar czf cikrf-csv-moscow-$DATE.tar.gz cikrf-csv-moscow-$DATE/
    tar czf cikrf-web-mo-$DATE.tar.gz     cikrf-web-mo-$DATE/
    tar czf cikrf-csv-mo-$DATE.tar.gz     cikrf-csv-mo-$DATE/
    mv cikrf-*-moscow-*.tar.gz data/moscow/
    mv cikrf-*-mo-*.tar.gz     data/mo/
    rm -rf cikrf-web-*/
    rm -rf cikrf-csv-*/
    sleep 30
done
