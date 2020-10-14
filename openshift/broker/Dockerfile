FROM registry.access.redhat.com/ubi8/python-38:latest
MAINTAINER "SatCI"

ENV HOME="/opt/app-root/src/broker"

# Install broker
RUN pip install broker
# TODO: When we move to latest OCP 4.x, add --chown=1001:0 flag
COPY broker_settings.yaml $HOME/broker_settings.yaml

ENV BROKER_DIRECTORY="$HOME"
WORKDIR "$HOME"

# TODO: Remove the USER root directive when on latest OCP 4.x, although no harm keeping it.
# Switch to user root in order to perform `chown` and `fix-permissions`
# This is required as COPY command above can inadvertantly set copied
# file ownership to root:root and cause `chown` and `fix-permissions` to fail
# This only happens on older versions OpenShift, latest Podman handles it well
USER root

RUN chown -R 1001:0 ${HOME} && \
    fix-permissions ${HOME} -P

USER 1001
