# Using config.json

## Lat & Lon

Latitude and longitude values that will be inserted into HTTP requests.

## SQL
Credentials for accessing local SQL database.
Includes username, password, database name and access link.

## Models:

Config file for each Model. The key name matches the SQL table name,
except in the case of MetEir, which outputs as 3 unique tables.<br>
Each model config contains:<br>

### Root:

JSON path to the array of time periods in the http request response.

### RequestType:

Type of HTTP request that will be called.
Can be 0, 1 or 2 to represent GET, POST or RAPIDAPI.<br>
GET performs a GET request.<br>
POST performs a POST request.<br>
RAPIDAPI appends an additional header tag to the GET request
(for use with rapidapi.com).

### URL:

URL to be used within the HTTP request.
URLs use %f in place of latitude and longitude, for use with String.format().

### Keys:

List of metrics to be queried.
The key is a standardised name to be used across this project,
the value is a json path to this metric in the response.

### Units:

List of multipliers, if any, to apply to the given metrics.
Used for conversion between units. e.g. kph -> knts. <br>
Units to be used are as follows:

| Metric        | Unit  |
|:--------------|:-----:|
| WindSpeed     |  kts  |
| WindGust      |  kts  |
| WindDir       |  deg  |
| Temperature   |  °C   |
| Pressure      | mbar  |
| Precipitation |  mm   |
| POP           | 0-100 |





