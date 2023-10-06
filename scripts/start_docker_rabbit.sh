docker run -d --hostname th2 --name some-rabbit -e RABBITMQ_DEFAULT_VHOST=th2 -p 15672:15672 -p 5672:5672 rabbitmq:3-management
