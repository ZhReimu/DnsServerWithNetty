import io.netty.bootstrap.Bootstrap
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioDatagramChannel
import io.netty.handler.codec.dns.*
import java.net.InetAddress

fun main() {
    val group = NioEventLoopGroup()
    Bootstrap().apply {
        group(group)
        channel(NioDatagramChannel::class.java)
        handler(object : ChannelInitializer<NioDatagramChannel>() {
            override fun initChannel(ch: NioDatagramChannel?) {
                ch?.pipeline()?.apply {
                    addLast(DatagramDnsQueryDecoder())
                    addLast(DatagramDnsResponseEncoder())
                    addLast(object : SimpleChannelInboundHandler<DatagramDnsQuery>() {
                        override fun channelRead0(ctx: ChannelHandlerContext?, msg: DatagramDnsQuery?) {
                            msg?.let {
                                val response = DatagramDnsResponse(it.recipient(), it.sender(), it.id())
                                try {
                                    val dnsQuestion = it.recordAt<DefaultDnsQuestion>(DnsSection.QUESTION)
                                    val queryDomain = dnsQuestion.name().removeSuffix(".")
                                    response.addRecord(DnsSection.QUESTION, dnsQuestion)
                                    response.isAuthoritativeAnswer = true

                                    println("查询 -> $queryDomain")
                                    when (dnsQuestion.type()) {
                                        DnsRecordType.PTR -> {
                                            println("处理 PTR")
                                            response.addRecord(
                                                DnsSection.ANSWER,
                                                DefaultDnsPtrRecord(
                                                    queryDomain,
                                                    DnsRecord.CLASS_IN,
                                                    2102,
                                                    "FuckDnsServer"
                                                )
                                            )
                                        }
                                        DnsRecordType.A -> {
                                            response.addRecord(
                                                DnsSection.ANSWER,
                                                DefaultDnsRawRecord(
                                                    queryDomain,
                                                    DnsRecordType.A,
                                                    10,
                                                    Unpooled.wrappedBuffer(InetAddress.getByName(queryDomain).address)
                                                )
                                            )
                                        }
                                        DnsRecordType.AAAA -> {
                                            response.addRecord(
                                                DnsSection.ANSWER,
                                                DefaultDnsRawRecord(
                                                    queryDomain,
                                                    DnsRecordType.AAAA,
                                                    100,
                                                    Unpooled.wrappedBuffer(InetAddress.getByName(queryDomain).address)
                                                )
                                            )
                                        }
                                        else -> {

                                        }
                                    }
                                } finally {
                                    ctx?.let {
                                        writeAndFlush(response)
                                        println("发送返回值 -> $response")
                                    }
                                }

                            }
                        }

                        override fun exceptionCaught(ctx: ChannelHandlerContext?, cause: Throwable?) {
                            println("出现异常 $cause")
                        }

                    })
                    option(ChannelOption.SO_BROADCAST, true)
                }
            }
        })
        bind("0.0.0.0", 53).apply {
            println("服务器启动成功!")
            sync().channel().closeFuture().sync()
        }
    }
}