package main

import (
	. "../message"
	"fmt"
	"net"
	"os"
	"os/signal"
	"syscall"
)

func readFromConn(conn *net.UDPConn) (int, *net.UDPAddr, []byte) {
	data := make([]byte, 1024)
	for true {
		n, addr, err := conn.ReadFromUDP(data)
		if err != nil {
			fmt.Printf("read failed from addr: %v, err: %v\r\n", addr, err)
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
	c := make(chan os.Signal)
	signal.Notify(c, syscall.SIGHUP, syscall.SIGINT, syscall.SIGTERM, syscall.SIGQUIT)
	args := os.Args

	var id byte = DIVICE

	if len(args) < 2 {
		fmt.Println("start device client as default.")
	} else if args[1] == "mobile" {
		fmt.Println("start mobile client.")
		id = MOBILE
	} else {
		fmt.Println("no support, start device client as default.")
	}

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
	ctx.id = id

	go func() {
		<-c
		ctx.onExit()
		os.Exit(0)
	}()

	if ctx.authenticate() {
		fmt.Println("connet to server success!")
		ctx.loop()
	} else {
		fmt.Println("connet to server failed!, client exit.")
	}

}
