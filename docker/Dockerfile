FROM hurence/logisland-hdp2.5
MAINTAINER Hurence


USER root


RUN cd /usr/local; \
    wget http://mirrors.standaloneinstaller.com/apache/maven/maven-3/3.5.0/binaries/apache-maven-3.5.0-bin.tar.gz; \
    tar -xzf apache-maven-3.5.0-bin.tar.gz;
ENV PATH /usr/local/apache-maven-3.5.0/bin:$PATH


# build
#RUN cd /usr/local; \
#    git clone https://github.com/Hurence/logisland.git logisland-src; \
#    cd logisland-src; \
#    git fetch; \
#    git checkout feature/machinelearning
#RUN cd /usr/local/logisland-src;     mvn install

# build jdev
RUN cd /usr/local; \
    git clone https://github.com/Hurence/logisland-flow-analytics-ml-jobs.git; \
    cd logisland-flow-analytics-ml-jobs; \
    mvn install

RUN cd /tmp; wget https://raw.githubusercontent.com/roberthryniewicz/datasets/master/svepisodes.json -O /tmp/svepisodes.json

# update boot script
COPY bootstrap.sh /etc/bootstrap.sh
RUN chown root.root /etc/bootstrap.sh
RUN chmod 700 /etc/bootstrap.sh

ENTRYPOINT ["/etc/bootstrap.sh"]
