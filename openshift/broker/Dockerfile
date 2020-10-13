FROM registry.access.redhat.com/ubi8/python-38:latest
MAINTAINER "SatCI"

ENV HOME="/opt/app-root/src/broker"

# Install broker
RUN pip install broker
COPY broker_settings.yaml $HOME/broker_settings.yaml

ENV BROKER_DIRECTORY="$HOME"
WORKDIR "$HOME"

RUN chown -R 1001:0 ${HOME} && \
    fix-permissions ${HOME} -P

USER 1001
