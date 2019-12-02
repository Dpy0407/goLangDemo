package main

import (
	"log"
	"net"
	"os"
	"strconv"
)

const UDP_PORT = 9090
const TCP_PORT = 8080

func init() {
	log.SetFlags(log.Lshortfile)
}

func main() {

	args := os.Args

	var udpPort, tcpPort int
	if len(args) < 2 {
		udpPort = UDP_PORT
		tcpPort = TCP_PORT
	} else if len(args) < 3 {
		p, err := strconv.Atoi(args[1])
		if err == nil {
			udpPort = p
		} else {
			udpPort = UDP_PORT
		}

		p, err = strconv.Atoi(args[2])
		if err == nil {
			tcpPort = p
		} else {
			tcpPort = TCP_PORT
		}
	}

	udpConn, err := net.ListenUDP("udp", &net.UDPAddr{
		IP:   net.IPv4(0, 0, 0, 0),
		Port: udpPort,
	})

	if err != nil {
		log.Printf("udp listen failed, err:%v\n", err)
		return
	}

	defer udpConn.Close()

	tcpListener, err := net.ListenTCP("tcp", &net.TCPAddr{
		IP:   net.IPv4(0, 0, 0, 0),
		Port: tcpPort,
	})

	if err != nil {
		log.Printf("tcp listen failed, err:%v\n", err)
		return
	}
	defer tcpListener.Close()

	log.Printf("server start, udp port = %d, tcp port = %d...\r\n", udpPort, tcpPort)

	processLoop(udpConn, tcpListener)

	log.Println("message from device!")

}
