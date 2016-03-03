#!/usr/bin/env python
# encoding: utf-8
from annoy import AnnoyIndex


def do(indextype):
    a = AnnoyIndex(8, indextype[0])
    a.load('points.%s.annoy' % indextype)
    with open('points.%s.ann.txt' % indextype, 'w') as out:
        for q_index in [1443, 1240, 818, 1725, 1290, 2031, 1117, 1211, 1902, 603]:
            nns = a.get_nns_by_item(q_index, 10)
            print >> out, '%s\t%s' % (q_index, ','.join([str(n) for n in nns]))

do('angular')
do('euclidean')
