package main

import (
	. "../message"
	"fmt"
	"net"
)

func readFromConn(conn *net.UDPConn) (int, *net.UDPAddr, []byte) {
	data := make([]byte, 1024)
	for true {
		n, addr, err := conn.ReadFromUDP(data)
		if err != nil {
			fmt.Println("read failed from addr: %v, err: %v\n", addr, err)
			continue
		}

		if n < MSG_BASE_LEN {
			fmt.Println("Invalid Data")
			continue
		}

		return n, addr, data
	}

	// never
	return -1, nil, nil
}

func main() {
	conn, err := net.DialUDP("udp", nil, &net.UDPAddr{
		IP:   net.IPv4(127, 0, 0, 1),
		Port: 9090,
	})

	if err != nil {
		fmt.Println("connet failed!", err)
		return
	}

	defer conn.Close()

	ctx := IClientContex{}
	ctx.conn = conn
	ctx.id = DIVICE

	if ctx.authenticate() {
		fmt.Println("connet to server success!")
		ctx.loop()
	} else {
		fmt.Println("connet to server failed!, client exit.")
	}
}
