package main

import (
	"fmt"
	"net"
)

const PORT = 9090

func main() {
	conn, err := net.ListenUDP("udp", &net.UDPAddr{
		IP:   net.IPv4(0, 0, 0, 0),
		Port: PORT,
	})

	if err != nil {
		fmt.Printf("listen failed, err:%v\n", err)
		return
	}

	defer conn.Close()
	fmt.Printf("server start, port = %d...\r\n", PORT)
	processLoop(conn)

	fmt.Println("message from device!")

}
