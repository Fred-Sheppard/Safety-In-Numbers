# Using config.json

## Queries:
A list of urls to query, where the resulting file will be named as the key.
Each query uses the Model config sharing a name with the text before the underscore.
Urls use %f in place of latitude and longitude, for use with String.format().<br>
e.g. "Accu_1h": "[url]" -> "Accu_1h.json", config = "Accu"

## Models:
Config file for each Model. Contains:<br>
### Root:
JSON path to the array of time periods in the http request response.
### Keys:
List of metrics to be queried.
The key is a standardised name to be used across this project,
the value is a json path to this metric in the response
### Units:
List of multipliers, if any, to apply to the given metrics.
Used for conversion between units. e.g. kph -> knts.


