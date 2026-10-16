FROM node:22-alpine
WORKDIR /app
COPY index.html feed.xml ./
COPY assets ./assets
COPY data ./data
COPY server ./server
COPY licenses ./licenses
USER node
ENV PORT=3000
EXPOSE 3000
CMD ["node","server/serve.mjs"]
