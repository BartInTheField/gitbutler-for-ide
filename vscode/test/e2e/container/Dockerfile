# Hermetic environment for the VSCode core e2e: Node (to run the compiled gitbutler-core
# JS package) plus the REAL `but` CLI. Mirrors the JetBrains integration-test image.
# libdbus-1-3 is REQUIRED — the `but` binary fails to load without it.
FROM node:20-bookworm-slim

RUN apt-get update && \
    apt-get install -y curl ca-certificates git libdbus-1-3 && \
    rm -rf /var/lib/apt/lists/*

# Latest `but` by design (the installer has no version pin) so the e2e catches CLI drift.
RUN curl -fsSL https://gitbutler.com/install.sh | sh && \
    ln -sf /root/.local/bin/but /usr/local/bin/but

RUN git config --global user.email "e2e@example.com" && \
    git config --global user.name "e2e" && \
    git config --global init.defaultBranch main

# Keeps the container alive so the test can exec commands into it.
CMD ["sleep", "infinity"]
