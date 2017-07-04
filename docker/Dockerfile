FROM hurence/logisland-hdp2.5
MAINTAINER Hurence


USER root


RUN cd /usr/local; \
    wget http://mirrors.standaloneinstaller.com/apache/maven/maven-3/3.5.0/binaries/apache-maven-3.5.0-bin.tar.gz; \
    tar -xzf apache-maven-3.5.0-bin.tar.gz;
ENV PATH /usr/local/apache-maven-3.5.0/bin:$PATH

RUN yum install -y java-1.8.0-openjdk-devel

# build jdev
RUN cd /usr/local;  \
    git clone https://github.com/Hurence/logisland-flow-analytics-ml-jobs.git;
RUN  cd /usr/local/logisland-flow-analytics-ml-jobs; \
     MAVEN_OPTS="-Xms512m -Xmx1024m -XX:PermSize=256m -XX:MaxPermSize=512m"; \
     mvn compile assembly:single; \
     mvn install
RUN cp /usr/local/logisland-flow-analytics-ml-jobs/target/logisland-flow-analytics-ml-jobs-0.10.1.jar  /usr/local/logisland/lib


RUN cd /tmp; \
    wget https://raw.githubusercontent.com/roberthryniewicz/datasets/master/svepisodes.json -O /tmp/svepisodes.json
RUN cd /tmp; \
    wget https://github.com/Hurence/logisland-flow-analytics-ml-jobs/releases/download/v0.1/capture20110810.binetflow.tgz; \
    tar xzf capture20110810.binetflow.tgz; \
    rm -f capture20110810.binetflow.tgz
RUN cd /tmp; \
    wget https://github.com/Hurence/logisland-flow-analytics-ml-jobs/releases/download/v0.1/pagecount_sm.dat.tgz; \
    tar xzf pagecount_sm.dat.tgz; \
    rm -f pagecount_sm.dat.tgz


#RUN    cd /usr/local/logisland-flow-analytics-ml-jobs; mvn compile assembly:single
COPY html /usr/local/logisland/_site

# update boot script
COPY bootstrap.sh /etc/bootstrap.sh
RUN chown root.root /etc/bootstrap.sh
RUN chmod 700 /etc/bootstrap.sh

ENTRYPOINT ["/etc/bootstrap.sh"]
