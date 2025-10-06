#!/bin/sh
srcdir="dist"
# dstdir="<target_dir>"

for f in ${srcdir}/*.jar
do
    docker cp $f keycloak:/opt/keycloak/providers
done