#!/usr/bin/env python
# encoding: utf-8
from annoy import AnnoyIndex

a = AnnoyIndex(8, 'a')
e = AnnoyIndex(8, 'e')
for n, l in enumerate(open('points.csv')):
    x = [float(f) for f in l.strip().split(',')]
    a.add_item(n, x)
    e.add_item(n, x)

a.build(-1)
a.save('points.angular.annoy')
e.build(-1)
e.save('points.euclidean.annoy')
