cd ../covid19
git pull
cd ../covid19_release
set FILE=index.md
set FILE2=ch.md
java -cp * charts.Covid19Charts ../covid19/%FILE%
java -cp * charts.Covid19Charts$SwissCharts ../covid19/%FILE2%
cd ../covid19
git add %FILE%
git add %FILE2%
git commit %FILE% -m "update"
git commit %FILE2% -m "update"
git push origin