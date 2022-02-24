FROM continuumio/miniconda3
RUN apt-get update --fix-missing && \
    apt-get install -y ntp && \
    apt-get clean
ADD environment.yml /tmp/environment.yml
RUN conda env create -f /tmp/environment.yml
# Pull the environment name out of the environment.yml
RUN echo "source activate $(head -1 /tmp/environment.yml | cut -d' ' -f2)" > ~/.bashrc
ENV PATH /opt/conda/envs/$(head -1 /tmp/environment.yml | cut -d' ' -f2)/bin:$PATH

RUN mkdir /usr/local/workdir
WORKDIR /usr/local/workdir