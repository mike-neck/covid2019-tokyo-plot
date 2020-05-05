@Grab('org.knowm.xchart:xchart:3.6.3')

import java.time.*
import groovy.transform.Canonical

def csvFile = new URL('https://stopcovid19.metro.tokyo.lg.jp/data/130001_tokyo_covid19_patients.csv')

String.metaClass.define {
    collectLine = { Closure c ->
        def list = []
        delegate.eachLine { list << c(it) }
        return list
    }
}

List.metaClass.define {
    collectWithIndex = { Closure c ->
        def list = []
        delegate.eachWithIndex { item, index -> list << c(item, index) }
        return list
    }
    collectNotNull = { Closure c ->
        def list = []
        delegate.each {
            def item = c(it)
            if (item != null) list << item
        }
        return list
    }
}

enum Sex { WOMEN, MEN, UNKNOWN }

@Canonical
class Patient {
    LocalDate date
    int age
    Sex sex
}

def data = csvFile.text.collectLine { it.split(',') }.collectNotNull {
    if (it[0].contains('No')) return null
    try {
        def date = LocalDate.parse(it[4])
        def age = it[8].replace('代','').replace('歳以上','').replace('歳未満','').replace('不明','-1').replace('-','-1') as int
        def sex = it[9].trim() == '男性'? Sex.MEN: it[9].trim() == '女性'? Sex.WOMEN: Sex.UNKNOWN
        new Patient(date:date, age: age, sex:sex)
    } catch (e) { 
        println e
        println it
    }
}

LocalDate.metaClass.define {
    next = {
        delegate.plusDays(1)
    }
}

class DateRange implements Iterable<LocalDate> {
    LocalDate start
    LocalDate endExclusive
    Iterator<LocalDate> iterator() {
        def current = start
        [
            hasNext: { current < endExclusive },
            next: {
                def v = current
                current = current.next()
                return v
            }
        ] as Iterator<LocalDate>
    }
}

class Count {
    Map<LocalDate, Integer> count

    int get(LocalDate date) {
        def e = count[date]
        if (e == null) 0
        else e
    }

    double get(DateRange range) {
        range.collect { get(it) }.stream().mapToInt { it }.summaryStatistics().average
    }
}

def count = new Count(count: data.countBy { it.date })


class Chart {
  String title
  Iterable<LocalDate> labels
  List<Number> plots

  def labelsAsDate() {
    def zone = ZoneOffset.ofHours(9)
    def time = LocalTime.of(0, 0)
    labels.collect { OffsetDateTime.of(it, time, zone).toInstant() }.collect { Date.from(it) }
  }

  def query() {
      def ls = labels.collect { "'$it'" }.join(',')
      def ps = plots.collect { "$it".trim() }.join(',')
      def ts = URLEncoder.encode(title, 'UTF-8')
      "c={type:'line',data:{labels:[$ls],datasets:[{label:'$ts',data:[$ps],fill:false,borderColor:'blue'}]}}"
  }

  def chart() {
      "https://quickchart.io/chart?${query()}"
  }
}

def range = new DateRange(start: data[0].date, endExclusive: data[data.size()-1].date.next())

def simpleChart = new Chart(title:'日別感染者数',labels:range,plots:range.collect { count.get(it) })

//println simpleChart.chart()

println()

def averageChart = new Chart(
  title: '7日移動平均', 
  labels:range,
  plots: range.collect {
    new DateRange(start:it.minusDays(6), endExclusive: it.next())
  }.collect { count.get(it) }
)

//println averageChart.chart()

println()

def threeDays = new Chart(
    title: '3日移動平均',
    labels:range,
    plots: range.collect { new DateRange(start:it.minusDays(2), endExclusive: it.next()) }.collect { count.get(it) }
)

//println threeDays.chart()

import org.knowm.xchart.XYChart
import org.knowm.xchart.SwingWrapper

def ch = new XYChart(600, 480)

ch.addSeries('日別感染者数', simpleChart.labelsAsDate(), simpleChart.plots)
ch.addSeries('7日移動平均', averageChart.labelsAsDate(), averageChart.plots)

new SwingWrapper(ch).displayChart()

