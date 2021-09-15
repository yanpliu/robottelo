FROM registry.access.redhat.com/ubi8/python-38:latest
MAINTAINER "SatCI"

ENV VIRTUAL_ENV="/opt/app-root" \
    HOME="/opt/app-root/src" \
    RP_TOOLS_DIR="/opt/app-root/src/report-portal-tools" \
    GIT_SSL_NO_VERIFY="true"

# Install report portal tools
RUN git clone https://gitlab-ci-token:${CONFIG_FILES_TOKEN}@gitlab.sat.engineering.redhat.com/satelliteqe/report-portal-tools.git && \
    cd report-portal-tools && \
    git log -1 && \
    pip install -r requirements.txt

# TODO: When we move to latest OCP 4.x, add --chown=1001:0 flag
COPY rp_conf.yaml $RP_TOOLS_DIR/scripts/reportportal_cli/rp_conf.yaml

# TODO: Remove the USER root directive when on latest OCP 4.x, although no harm keeping it.
# Switch to user root in order to perform `chown` and `fix-permissions`
# This is required as COPY command above can inadvertantly set copied
# file ownership to root:root and cause `chown` and `fix-permissions` to fail
# This only happens on older versions OpenShift, latest Podman handles it well
USER 0

RUN chgrp -R 0 ${VIRTUAL_ENV} && \
    chown -R 1001:0 ${VIRTUAL_ENV} && \
    fix-permissions ${VIRTUAL_ENV} -P

USER 1001
WORKDIR "${RP_TOOLS_DIR}"
