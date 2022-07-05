#!/bin/sh

curl -s -d "$*" -H 'content-type: text/plain' "@@SearchUrl@@"
