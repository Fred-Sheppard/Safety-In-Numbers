import csv

import matplotlib.pyplot as plt

from Styler import setStyle

playerDict = {}
with open("C:/Personal/Code/Graphing/data/Accu_1h.csv", newline='') as csvfile:
    my_reader = csv.reader(csvfile)
    next(my_reader, None)
    for row in my_reader:
        player: str = row[0]
        if player not in playerDict:
            playerDict[player] = 0
        playerDict[player] += float(row[1])

sortedDict = playerDict
keys = list(sortedDict.keys())
vals = list(sortedDict.values())

setStyle()
plt.plot(keys, vals)
plt.xlabel('Offset')
plt.ylabel('Variance')
plt.title("AccuWeather Variance vs Offset")
# plt.xticks(range(0, 48, 2))
plt.show()
