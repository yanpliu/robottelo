FROM registry.access.redhat.com/ubi8/python-38:latest
MAINTAINER "SatCI"

ENV HOME="/opt/app-root/src/robottelo" \
    BROKER_DIRECTORY="/opt/app-root/src/robottelo" \
    PYCURL_SSL_LIBRARY=openssl

USER 0
RUN yum -y install https://dl.fedoraproject.org/pub/epel/8/Everything/x86_64/Packages/c/crudini-0.9.3-1.el8.noarch.rpm && \
    yum -y clean all

USER 1001
RUN git clone https://github.com/SatelliteQE/robottelo.git && \
    cd robottelo && \
    pip install -r requirements.txt && \
    git clone --depth 1 https://gitlab-ci-token:${CONFIG_FILES_TOKEN}@gitlab.sat.engineering.redhat.com/satelliteqe/jenkins-configs.git conf && \
    cp conf/robottelo.properties robottelo.properties && \
    cp conf/virtwho.properties virtwho.properties && \
    cp conf/*.yaml . && \
    crudini --set robottelo.properties server ssh_key conf/ssh_keys/id_hudson_rsa && \
    crudini --set robottelo.properties bugzilla api_key ${BUGZILLA_API_KEY} && \
    crudini --set robottelo.properties robottelo webdriver chrome && \
    crudini --set robottelo.properties robottelo webdriver_desired_capabilities "platform=ANY,maxDuration=5400,idleTimeout=1000,start-maximised=true,screenResolution=1600x1200,tags=[robottelo-container]" && \
    chmod -cR go+w ${HOME}

# TODO: When we move to latest OCP 4.x, add --chown=1001:0 flag
COPY broker_settings.yaml $HOME/broker_settings.yaml

WORKDIR "$HOME"
