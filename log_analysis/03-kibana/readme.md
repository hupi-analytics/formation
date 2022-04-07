## Settings
 * index name or pattern : apache_log-*
 * Time-field name : @timestamp

## Visualize
* metrics
  * Y-Axis
    * aggregation -> Count
* buckets
  * X-Axis
    * aggregation -> Date Histogram
    * field -> @timestamp
    * interval -> Minute
  * Split Lines
    * Sub Aggregation -> Terms
    * Field -> response
    * Oder By -> Term
    * Order -> Descending
    * Size -> 0
