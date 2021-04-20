#!/bin/bash
source activate $(head -1 /tmp/environment.yml | cut -d ' ' -f2 | sed -e 's/[\r\n]*//g')
export PATH=/opt/conda/envs/$(head -1 /tmp/environment.yml | cut -d' ' -f2 | sed -e 's/[\r\n]*//g')/bin:$PATH

$1 $2
exit $?