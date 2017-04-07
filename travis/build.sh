#!/bin/bash

lein test
lein cloverage --coveralls
