annoy-java
==========

[![Build Status](https://travis-ci.org/spotify/annoy-java.svg?branch=master)](https://travis-ci.org/spotify/annoy-java)
[![GitHub license](https://img.shields.io/github/license/spotify/annoy-java.svg)](./LICENSE)
[![Maven Central](https://img.shields.io/maven-central/v/com.spotify/annoy.svg)](https://maven-badges.herokuapp.com/maven-central/com.spotify/annoy)

`annoy-java` is a Java client for [annoy](https://github.com/spotify/annoy).

It was built to give us access to ANN queries from JVM languages, for indices
built by other Python pipelines.

# Limitations

- annoy-java only implements loading trees built by the Python version of
  annoy; it cannot yet create its own.

# License

Copyright 2016 Spotify AB.

Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
