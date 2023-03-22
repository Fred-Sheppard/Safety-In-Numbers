from cycler import cycler
from matplotlib import pyplot as plt


def setStyle():
    plt.rcParams['axes.facecolor'] = '#333333'
    COLOR = 'white'
    plt.rcParams['text.color'] = COLOR
    plt.rcParams['axes.labelcolor'] = COLOR
    plt.rcParams['xtick.color'] = COLOR
    plt.rcParams['ytick.color'] = COLOR
    plt.rcParams['axes.prop_cycle'] = cycler(color=["white"])
    plt.figure(facecolor='#222222')
