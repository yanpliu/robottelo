FROM registry.access.redhat.com/ubi8/python-38:latest
MAINTAINER "SatCI"

ENV HOME="/opt/app-root/src/robottelo" \
    PYCURL_SSL_LIBRARY=openssl

USER 0
RUN yum -y install https://dl.fedoraproject.org/pub/epel/8/Everything/x86_64/Packages/c/crudini-0.9.3-1.el8.noarch.rpm

USER 1001
RUN git clone https://github.com/SatelliteQE/robottelo.git && \
    cd robottelo && \
    pip install -r requirements.txt "pytest-xdist==1.34.0" && `# FIXME: remove pytest-xdist when moved to requirements.txt` \
    git clone --depth 1 https://gitlab-ci-token:${CONFIG_FILES_TOKEN}@gitlab.sat.engineering.redhat.com/satelliteqe/jenkins-configs.git conf && \
    cp conf/robottelo.properties robottelo.properties && \
    cp conf/*.yaml . && \
    crudini --set robottelo.properties bugzilla api_key ${BUGZILLA_API_KEY} && \
    crudini --set robottelo.properties robottelo webdriver chrome && \
    crudini --set robottelo.properties robottelo webdriver_desired_capabilities "platform=ANY,maxDuration=5400,idleTimeout=1000,start-maximised=true,screenResolution=1600x1200,tags=[robottelo-container]" && \
    chmod -c go+w ${HOME}

WORKDIR "$HOME"
