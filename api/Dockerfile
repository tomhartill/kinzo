FROM golang:1.26 AS build
WORKDIR /src
COPY go.mod go.sum ./
RUN go mod download
COPY . .
RUN CGO_ENABLED=0 go build -trimpath -ldflags="-s -w" -o /out/kinzo-api ./cmd/kinzo-api

FROM gcr.io/distroless/static-debian12:nonroot
COPY --from=build /out/kinzo-api /kinzo-api
EXPOSE 8080
ENTRYPOINT ["/kinzo-api"]
