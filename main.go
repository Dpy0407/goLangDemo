package main

import (
	"fmt"
	"net"
)

var limitChan = make(chan bool, 10)

var dataFromDeviceChan = make(chan []byte, 2)

func main() {
	conn, err := net.ListenUDP("udp", &net.UDPAddr{
		IP:   net.IPv4(0, 0, 0, 0),
		Port: 9090,
	})

	if err != nil {
		fmt.Printf("listen failed, err:%v\n", err)
		return
	}

	defer conn.Close()

	processLoop(conn)

	fmt.Println("message from device!")

}
