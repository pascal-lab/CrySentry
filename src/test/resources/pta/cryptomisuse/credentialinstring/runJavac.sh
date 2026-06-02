#!/bin/bash
for file in $(ls)
do
javac $file
done
