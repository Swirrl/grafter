#!/bin/bash

chown -R travis ../travis/*
mkdir -p /etc/leiningen/
mv ./travis/profiles.clj /etc/leiningen/profiles.clj
