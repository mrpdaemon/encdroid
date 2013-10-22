#!/bin/bash

# Usage: convert.sh <svg path> <asset name>

mkdir -p drawable-ldpi drawable-mdpi drawable-hdpi drawable-xhdpi drawable-xxhdpi

CUR_DIR=$(pwd)

inkscape -e $CUR_DIR/drawable-ldpi/$2 -w 36 -h 36 $1
inkscape -e $CUR_DIR/drawable-mdpi/$2 -w 48 -h 48 $1
inkscape -e $CUR_DIR/drawable-hdpi/$2 -w 72 -h 72 $1
inkscape -e $CUR_DIR/drawable-xhdpi/$2 -w 96 -h 96 $1
inkscape -e $CUR_DIR/drawable-xxhdpi/$2 -w 144 -h 144 $1
