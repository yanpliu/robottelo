FROM registry.access.redhat.com/ubi8/python-38:latest
MAINTAINER "SatCI"

ENV HOME="/opt/app-root/src/cloud-cleanup"
    BROKER_DIRECTORY="$HOME"

USER 1001
RUN git clone --depth 1 https://gitlab-ci-token:${CONFIG_FILES_TOKEN}@gitlab.sat.engineering.redhat.com/satelliteqe/satelliteqe-jenkins-tools.git && \
    cd resource_cleanup && \
    pip install -r requirements.txt && \
    chmod -cR go+w ${HOME}

WORKDIR "$HOME"
