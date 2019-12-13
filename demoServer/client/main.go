package main

import (
	. "../message"
	"flag"
	"log"
	"net"
	"os"
	"os/signal"
	"syscall"
)

var (
	help     bool
	remote   string
	ctype    string
	filePath string
)

func init() {
	flag.BoolVar(&help, "h", false, "show help info")
	flag.StringVar(&remote, "r", "", "set remot ip & port, eg. 127.0.0.1:8080")
	flag.StringVar(&ctype, "t", "device", "set client type, <device/mobile>")
	flag.StringVar(&filePath, "f", "", "load file to transmite")

	log.SetFlags(log.Lshortfile)
}

func main() {

	flag.Parse()
	if help {
		flag.Usage()
		return
	}

	var addrString string

	c := make(chan os.Signal)
	signal.Notify(c, syscall.SIGHUP, syscall.SIGINT, syscall.SIGTERM, syscall.SIGQUIT)

	if filePath != "" {
		// using tcp transmite
		ctype = "mobile"
	}

	var id byte = DEVICE

	if ctype == "mobile" {
		log.Println("start mobile client.")
		id = MOBILE
	} else if ctype == "device" {
		log.Println("start device client.")
		id = DEVICE
	} else {
		log.Println("no support, start device client as default.")
	}

	if remote != "" {
		addrString = remote
	} else {
		if DEVICE == id {
			addrString = "127.0.0.1:9090"
		} else {
			addrString = "127.0.0.1:8081"
		}
	}

	addr, err := net.ResolveTCPAddr("tcp", addrString)

	if err != nil {
		addr.IP = net.IP{127, 0, 0, 1}
		if DEVICE == id {
			addr.Port = 9090
		} else {
			addr.Port = 8081
		}

	}

	log.Printf("connet to %v...\r\n", addr)
	ctx := IClientContex{}
	ctx.id = id

	if DEVICE == ctx.id {
		udpConn, err := net.DialUDP("udp", nil, &net.UDPAddr{
			IP:   addr.IP,
			Port: addr.Port,
		})

		if err != nil {
			log.Println("connet failed!", err)
			return
		}

		defer udpConn.Close()

		ctx.udpConn = udpConn
	} else if MOBILE == ctx.id {
		tcpConn, err := net.DialTCP("tcp", nil, &net.TCPAddr{
			IP:   addr.IP,
			Port: addr.Port,
		})

		if err != nil {
			log.Println("connet failed!", err)
			return
		}

		defer tcpConn.Close()
		ctx.tcpConn = tcpConn
	}

	go func() {
		<-c
		ctx.onExit()
		os.Exit(0)
	}()

	if filePath != "" {
		ctx.fileTranMode = true
		ctx.fileInfo.FilePath = filePath
	}

	if ctx.authenticate() {
		log.Println("connet to server success!")
		ctx.loop()
	} else {
		log.Println("connet to server failed!, client exit.")
	}

}
