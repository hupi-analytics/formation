#!/bin/bash

docker -H localhost run --rm --name exemple_creation_docker --net host --env-file /home/ecoles-projets/bureau_etudes_insa/2021/exemple_creation_docker/env.file -v "/home/ecoles-projets/bureau_etudes_insa/2021/exemple_creation_docker:/usr/local/workdir" docker.hupi.io/docker-ecoles/hupi/ecoles-projets/exemple_image /bin/bash start.sh Rscript ./src/main.r