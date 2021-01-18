FROM registry.access.redhat.com/ubi8/python-38:latest
MAINTAINER "SatCI"

# REQUESTS_CA_BUNDLE is set for ibutsu client's requests use
ENV HOME="/opt/app-root/src/robottelo" \
    BROKER_DIRECTORY="/opt/app-root/src/robottelo" \
    PYCURL_SSL_LIBRARY=openssl \
    REQUESTS_CA_BUNDLE="/etc/pki/tls/certs/ca-bundle.crt"

USER 0
RUN yum -y install https://dl.fedoraproject.org/pub/epel/8/Everything/x86_64/Packages/c/crudini-0.9.3-1.el8.noarch.rpm && \
    yum -y clean all && \
    curl https://password.corp.redhat.com/RH-IT-Root-CA.crt -o /etc/pki/ca-trust/source/anchors/RH-IT-Root-CA.crt && \
    update-ca-trust

USER 1001
RUN git clone https://github.com/SatelliteQE/robottelo.git && \
    cd robottelo && \
    pip install -r requirements.txt && \
    git clone --depth 1 https://gitlab-ci-token:${CONFIG_FILES_TOKEN}@gitlab.sat.engineering.redhat.com/satelliteqe/jenkins-configs.git conf && \
    cp conf/robottelo.properties robottelo.properties && \
    cp conf/virtwho.properties virtwho.properties && \
    cp conf/*.yaml . && \
    cat ${SATLAB_AUTOMATION_PRIVATE_KEY} > .satlab_automation_rsa && \
    crudini --set robottelo.properties server ssh_key .satlab_automation_rsa && \
    crudini --set robottelo.properties bugzilla api_key ${BUGZILLA_API_KEY} && \
    crudini --set robottelo.properties robottelo webdriver chrome && \
    crudini --set robottelo.properties robottelo webdriver_desired_capabilities "platform=ANY,maxDuration=5400,idleTimeout=1000,start-maximised=true,screenResolution=1600x1200,tags=[robottelo-container]" && \
    chmod -cR go+w /opt/app-root

# TODO: When we move to latest OCP 4.x, add --chown=1001:0 flag
COPY broker_settings.yaml $HOME/broker_settings.yaml

WORKDIR "$HOME"
